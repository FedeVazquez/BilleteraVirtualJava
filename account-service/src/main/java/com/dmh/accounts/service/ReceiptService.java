package com.dmh.accounts.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.accounts.model.Account;
import com.dmh.accounts.model.Transaction;
import com.dmh.accounts.security.AccountAccess;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Comprobante en PDF de un movimiento (opcional del sprint 4). Muestra los mismos datos que el
 * detalle de la actividad.
 */
@Service
public class ReceiptService {

    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final Color BRAND = new Color(0x1F, 0x3A, 0x5F);

    private final AccountAccess accountAccess;
    private final ActivityService activityService;
    private final DateTimeFormatter dateFormat;

    public ReceiptService(AccountAccess accountAccess, ActivityService activityService,
            @Value("${app.timezone}") String timezone) {
        this.accountAccess = accountAccess;
        this.activityService = activityService;
        this.dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", AR).withZone(ZoneId.of(timezone));
    }

    @Transactional(readOnly = true)
    public byte[] receipt(Long accountId, Long transactionId) {
        Account account = accountAccess.requireAccess(accountId);
        Transaction transaction = activityService.findOwned(accountId, transactionId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.addTitle("Comprobante de operación " + transaction.getId());
        document.addCreator("Digital Money House");
        document.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
        Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
        Font amountFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.BLACK);

        document.add(new Paragraph("Digital Money House", title));
        document.add(new Paragraph("Comprobante de " + operationName(transaction).toLowerCase(AR), subtitle));
        Paragraph amount = new Paragraph(sign(transaction) + money(transaction.getAmount()), amountFont);
        amount.setSpacingBefore(14);
        amount.setSpacingAfter(14);
        document.add(amount);

        PdfPTable table = new PdfPTable(new float[] { 2f, 3f });
        table.setWidthPercentage(100);
        row(table, "Operación N.º", String.valueOf(transaction.getId()));
        row(table, "Fecha y hora", dateFormat.format(transaction.getDated()));
        row(table, "Tipo", operationName(transaction));
        row(table, "Descripción", transaction.getDescription());
        switch (transaction.getType()) {
            case DEPOSIT -> {
                row(table, "Origen", "Tarjeta terminada en " + transaction.getCardLastFour());
                row(table, "Destino", "Cuenta propia");
                ownAccount(table, account, "Destino");
            }
            case TRANSFER_OUT -> {
                ownAccount(table, account, "Origen");
                counterpart(table, transaction, "Destino");
            }
            case TRANSFER_IN -> {
                counterpart(table, transaction, "Origen");
                ownAccount(table, account, "Destino");
            }
        }
        document.add(table);

        Paragraph footer = new Paragraph("Comprobante generado por Digital Money House. Conservalo como constancia de la operación.",
                FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
        footer.setSpacingBefore(16);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
        document.close();
        return out.toByteArray();
    }

    private static String operationName(Transaction transaction) {
        return switch (transaction.getType()) {
            case DEPOSIT -> "Ingreso desde tarjeta";
            case TRANSFER_OUT -> "Transferencia enviada";
            case TRANSFER_IN -> "Transferencia recibida";
        };
    }

    private static String sign(Transaction transaction) {
        return transaction.getType().isIncome() ? "+ " : "- ";
    }

    private static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(AR).format(amount);
    }

    private static void ownAccount(PdfPTable table, Account account, String role) {
        row(table, role + " · CVU", account.getCvu());
        row(table, role + " · Alias", account.getAlias());
    }

    private static void counterpart(PdfPTable table, Transaction transaction, String role) {
        if (transaction.getCounterpartName() != null) {
            row(table, role + " · Titular", transaction.getCounterpartName());
        }
        row(table, role + " · CVU", transaction.getCounterpartCvu());
        row(table, role + " · Alias", transaction.getCounterpartAlias());
    }

    private static void row(PdfPTable table, String label, String value) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, valueFont));
        for (PdfPCell cell : new PdfPCell[] { labelCell, valueCell }) {
            cell.setBorderColor(new Color(0xCB, 0xD5, 0xE1));
            cell.setPadding(6);
        }
        labelCell.setBackgroundColor(new Color(0xF1, 0xF5, 0xF9));
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}
