"""Arma la planilla de casos de prueba (docs/testing/casos-de-prueba.xlsx) a partir del resultado
de run_test_suite.py (docs/testing/resultados-ejecucion.json).

Uso:  python scripts/build_test_sheet.py
Solo usa la libreria estandar de Python 3 (un .xlsx es un ZIP con archivos XML).
"""
import json
import os
import zipfile
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS = os.path.join(ROOT, "docs", "testing", "resultados-ejecucion.json")
OUTPUT = os.path.join(ROOT, "docs", "testing", "casos-de-prueba.xlsx")

# Indices de estilo (ver STYLES mas abajo)
DEFAULT, HEADER, TEXT, PASSED, FAILED, BLOCKED, BOLD, TITLE, CENTER = range(9)
STATUS_STYLE = {"Pasó": PASSED, "Falló": FAILED, "Bloqueado": BLOCKED}

COLUMNS = [  # (titulo, clave en el JSON, ancho, estilo)
    ("ID", "id", 9, CENTER), ("Historia / épica", "historia", 22, TEXT), ("Título", "titulo", 38, TEXT),
    ("Precondiciones", "precondiciones", 30, TEXT), ("Pasos", "pasos", 52, TEXT),
    ("Resultado esperado", "esperado", 46, TEXT), ("Tipo", "tipo", 11, CENTER), ("Prioridad", "prioridad", 10, CENTER),
    ("Suite", "suite", 18, CENTER), ("Test automatizado (JUnit)", "junit", 44, TEXT),
    ("Ejecución", "ejecucion", 11, CENTER), ("Estado", "estado", 12, None), ("Resultado obtenido", "obtenido", 52, TEXT),
    ("Fecha", "fecha", 12, CENTER),
]
STATUS_COLUMN = "L"
SUITE_COLUMN = "I"

STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="4">
<font><sz val="10"/><name val="Calibri"/></font>
<font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
<font><b/><sz val="10"/><name val="Calibri"/></font>
<font><b/><sz val="14"/><name val="Calibri"/></font>
</fonts>
<fills count="6">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF1F3A5F"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFC6EFCE"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFEB9C"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FFB0B0B0"/></left><right style="thin"><color rgb="FFB0B0B0"/></right><top style="thin"><color rgb="FFB0B0B0"/></top><bottom style="thin"><color rgb="FFB0B0B0"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="9">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top"/></xf>
<xf numFmtId="0" fontId="2" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top"/></xf>
<xf numFmtId="0" fontId="2" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top" wrapText="1"/></xf>
</cellXfs>
</styleSheet>"""


def column_letter(index):  # 0 -> A, 25 -> Z, 26 -> AA
    letters = ""
    index += 1
    while index:
        index, remainder = divmod(index - 1, 26)
        letters = chr(65 + remainder) + letters
    return letters


def cell(ref, value, style=DEFAULT):
    if isinstance(value, str) and value.startswith("="):
        return f'<c r="{ref}" s="{style}"><f>{escape(value[1:])}</f></c>'
    if isinstance(value, (int, float)):
        return f'<c r="{ref}" s="{style}"><v>{value}</v></c>'
    return f'<c r="{ref}" s="{style}" t="inlineStr"><is><t xml:space="preserve">{escape(str(value))}</t></is></c>'


def sheet_xml(rows, widths=None, freeze_header=False, autofilter=None):
    """rows: lista de filas; cada fila es una lista de (valor, estilo) o None para celda vacia."""
    parts = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
             '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">']
    if freeze_header:
        parts.append('<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" '
                     'activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>')
    if widths:
        parts.append("<cols>" + "".join(
            f'<col min="{i + 1}" max="{i + 1}" width="{width}" customWidth="1"/>' for i, width in enumerate(widths)) + "</cols>")
    parts.append("<sheetData>")
    for row_number, row in enumerate(rows, start=1):
        cells = "".join(cell(f"{column_letter(i)}{row_number}", *item) for i, item in enumerate(row) if item is not None)
        parts.append(f'<row r="{row_number}">{cells}</row>')
    parts.append("</sheetData>")
    if autofilter:
        parts.append(f'<autoFilter ref="{autofilter}"/>')
    parts.append("</worksheet>")
    return "".join(parts)


def cases_sheet(cases):
    rows = [[(title, HEADER) for title, _, _, _ in COLUMNS]]
    for case in cases:
        rows.append([(case.get(key, ""), style if style is not None else STATUS_STYLE.get(case["estado"], CENTER))
                     for _, key, _, style in COLUMNS])
    last = f"{column_letter(len(COLUMNS) - 1)}{len(rows)}"
    return sheet_xml(rows, widths=[width for _, _, width, _ in COLUMNS], freeze_header=True, autofilter=f"A1:{last}")


def summary_sheet(data, all_cases_sheet, smoke_sheet):
    def count(sheet, column, text):
        return f"=COUNTIF('{sheet}'!{column}:{column},\"{text}\")"

    def block(title, sheet):
        return [
            [(title, BOLD)],
            [("Estado", HEADER), ("Cantidad", HEADER)],
            [("Pasó", PASSED), (count(sheet, STATUS_COLUMN, "Pasó"), CENTER)],
            [("Falló", FAILED), (count(sheet, STATUS_COLUMN, "Falló"), CENTER)],
            [("Bloqueado", BLOCKED), (count(sheet, STATUS_COLUMN, "Bloqueado"), CENTER)],
            [("Total", BOLD), (f"=COUNTA('{sheet}'!A:A)-1", CENTER)],
            [],
        ]

    rows = [
        [("Digital Money House - Sprint 1 - Casos de prueba", TITLE)],
        [],
        [("Última ejecución", BOLD), (data["ejecutado"].replace("T", " "), DEFAULT)],
        [("Entorno", BOLD), (data["entorno"] + "  (perfil h2: base en memoria; ver TC-086 y TC-087)", DEFAULT)],
        [("Cómo se ejecutó", BOLD), ("python scripts/run_test_suite.py (los casos \"Manual\" se ejecutaron a mano)", DEFAULT)],
        [],
    ]
    rows += block("Suite de regresión (todos los casos)", all_cases_sheet)
    rows += block("Suite de humo (smoke)", smoke_sheet)
    rows += [
        [("Criterios", BOLD)],
        [("Smoke", BOLD), ("Camino feliz de cada historia y verificaciones mínimas de seguridad e infraestructura. "
                           "Si falla, el build no es apto para seguir probando.", DEFAULT)],
        [("Regresión", BOLD), ("Todos los casos, incluidos los de smoke. Se ejecuta antes de cada entrega.", DEFAULT)],
    ]
    return sheet_xml(rows, widths=[38, 16])


def main():
    with open(RESULTS, encoding="utf-8") as f:
        data = json.load(f)
    cases = data["casos"]
    smoke = [case for case in cases if case["suite"].startswith("Smoke")]
    sheets = [
        ("Resumen", None),
        ("Casos de prueba", cases_sheet(cases)),
        ("Suite Smoke", cases_sheet(smoke)),
    ]
    sheets[0] = ("Resumen", summary_sheet(data, "Casos de prueba", "Suite Smoke"))

    content_types = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                     '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                     '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
                     '<Default Extension="xml" ContentType="application/xml"/>'
                     '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
                     '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
                     + "".join(f'<Override PartName="/xl/worksheets/sheet{i}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
                               for i in range(1, len(sheets) + 1))
                     + "</Types>")
    root_rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                 '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                 '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
                 '</Relationships>')
    workbook = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
                'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>'
                + "".join(f'<sheet name="{escape(name)}" sheetId="{i}" r:id="rId{i}"/>' for i, (name, _) in enumerate(sheets, start=1))
                # fullCalcOnLoad: las formulas del resumen se calculan al abrir el archivo
                + '</sheets><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>')
    workbook_rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                     '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                     + "".join(f'<Relationship Id="rId{i}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet{i}.xml"/>'
                               for i in range(1, len(sheets) + 1))
                     + f'<Relationship Id="rId{len(sheets) + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
                     + "</Relationships>")

    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as xlsx:
        xlsx.writestr("[Content_Types].xml", content_types)
        xlsx.writestr("_rels/.rels", root_rels)
        xlsx.writestr("xl/workbook.xml", workbook)
        xlsx.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
        xlsx.writestr("xl/styles.xml", STYLES)
        for i, (_, xml) in enumerate(sheets, start=1):
            xlsx.writestr(f"xl/worksheets/sheet{i}.xml", xml)
    print(f"{os.path.relpath(OUTPUT, ROOT)}: {len(cases)} casos ({len(smoke)} en la suite de humo)")


if __name__ == "__main__":
    main()
