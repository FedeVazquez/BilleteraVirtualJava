"""Servidor SMTP falso para desarrollo SIN Docker (reemplaza a MailHog).

Acepta cualquier mail, no lo envia a ningun lado: lo muestra por consola y lo agrega como una linea
JSON al archivo indicado. Solo usa la libreria estandar de Python 3.

Uso:  python scripts/fake_smtp.py [puerto] [archivo_salida]
      (por defecto: puerto 1025, archivo mails.jsonl)
"""
import email
import email.policy
import json
import socketserver
import sys
from datetime import datetime

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 1025
OUTPUT = sys.argv[2] if len(sys.argv) > 2 else "mails.jsonl"


class SmtpHandler(socketserver.StreamRequestHandler):

    def reply(self, line):
        self.wfile.write((line + "\r\n").encode("ascii"))

    def handle(self):
        sender, recipients = None, []
        self.reply("220 fake-smtp listo")
        while True:
            raw = self.rfile.readline()
            if not raw:
                return
            command = raw.decode("utf-8", "replace").strip()
            verb = command.upper()
            if verb.startswith(("EHLO", "HELO")):
                self.reply("250 fake-smtp")
            elif verb.startswith("MAIL FROM:"):
                sender = command[10:].strip().split(" ")[0].strip("<>")
                self.reply("250 OK")
            elif verb.startswith("RCPT TO:"):
                recipients.append(command[8:].strip().strip("<>"))
                self.reply("250 OK")
            elif verb == "DATA":
                self.reply("354 Fin de datos con <CR><LF>.<CR><LF>")
                lines = []
                while True:
                    data_line = self.rfile.readline()
                    if not data_line or data_line in (b".\r\n", b".\n"):
                        break
                    # "dot-stuffing": una linea que empieza con punto llega con un punto de mas
                    lines.append(data_line[1:] if data_line.startswith(b"..") else data_line)
                self.store(sender, recipients, b"".join(lines))
                sender, recipients = None, []
                self.reply("250 OK")
            elif verb in ("RSET", "NOOP"):
                self.reply("250 OK")
            elif verb == "QUIT":
                self.reply("221 Chau")
                return
            else:
                self.reply("502 Comando no implementado")

    @staticmethod
    def store(sender, recipients, raw):
        message = email.message_from_bytes(raw, policy=email.policy.default)
        body = message.get_body(preferencelist=("plain", "html"))
        record = {
            "receivedAt": datetime.now().isoformat(timespec="seconds"),
            "from": sender,
            "to": recipients,
            "subject": str(message.get("Subject", "")),
            "body": body.get_content() if body else "",
        }
        with open(OUTPUT, "a", encoding="utf-8") as output:
            output.write(json.dumps(record, ensure_ascii=False) + "\n")
        print(f"--- mail para {', '.join(recipients)}: {record['subject']}\n{record['body']}", flush=True)


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    print(f"SMTP falso escuchando en localhost:{PORT} (mails en {OUTPUT})", flush=True)
    with Server(("127.0.0.1", PORT), SmtpHandler) as server:
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
