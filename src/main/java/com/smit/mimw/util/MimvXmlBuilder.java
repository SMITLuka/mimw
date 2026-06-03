package com.smit.mimw.util;

import com.smit.mimw.dto.FormUpdateData;
import com.smit.mimw.dto.MimvDetaljItem;
import com.smit.mimw.dto.ZaglavljeUpdateData;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Builds the ET405AA XML string from MIMV form data.
 * Conforms to etrosET405AA.xsd, etrosSimpleType.xsd, and etrosComplexType.xsd.
 * Optional XSD fields are omitted when null, zero, or blank to avoid schema violations.
 */
public final class MimvXmlBuilder
{
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Map<String, String> SIFOBR_MAP = Map.of(
            "MV02", "401", //$NON-NLS-1$ //$NON-NLS-2$
            "MV03", "405"  //$NON-NLS-1$ //$NON-NLS-2$
    );

    private MimvXmlBuilder()
    {
    }

    /**
     * Builds an ET405AA XML string from the provided form data.
     *
     * @param oib       taxpayer OIB fetched from AS400 (spaces stripped internally)
     * @param data      form key fields, zaglavlje display fields, and detalji vehicle rows
     * @param totalNew  sum of obracunatiIznosPP for new / test vehicles (statusVozila != R)
     * @param totalUsed sum of obracunatiIznosPP for used vehicles (statusVozila == R)
     * @return UTF-8 XML string
     */
    public static String build(String oib, FormUpdateData data, BigDecimal totalNew, BigDecimal totalUsed)
    {
        ZaglavljeUpdateData z        = data.getZaglavlje();
        List<MimvDetaljItem> detalji = data.getDetalji();

        String cleanOib   = oib != null ? oib.replaceAll("\\s+", "") : ""; //$NON-NLS-1$ //$NON-NLS-2$
        String taxCode    = data.getTaxPayerCode() != null ? data.getTaxPayerCode().toUpperCase() : ""; //$NON-NLS-1$
        String sifobr     = SIFOBR_MAP.getOrDefault(taxCode, ""); //$NON-NLS-1$
        int    formDate   = data.getFormDate()                 != null ? data.getFormDate()                 : 0;
        int    seqNum     = data.getSequentialNumberInPeriod() != null ? data.getSequentialNumberInPeriod() : 1;
        int    versionNum = data.getVersionNumber()            != null ? data.getVersionNumber()            : 1;
        String actionCode = z != null && z.getActionCode() != null ? z.getActionCode() : "N"; //$NON-NLS-1$

        String identifikator = String.format("%s-%08d-%s-%02d-%03d", //$NON-NLS-1$
                cleanOib, formDate, sifobr, seqNum, versionNum);

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"); //$NON-NLS-1$
        sb.append("<ET405AA xmlns=\"http://apisit.hr/b18/etrosarine/ET405AA\">\n"); //$NON-NLS-1$
        sb.append("  <Akcija>").append(esc(actionCode)).append("</Akcija>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("  <MIMVObrazac>\n"); //$NON-NLS-1$
        sb.append("    <Identifikator>").append(esc(identifikator)).append("</Identifikator>\n"); //$NON-NLS-1$ //$NON-NLS-2$

        if (z != null && z.getDateFrom() != null)
        {
            sb.append("    <RazdobljeOd>").append(z.getDateFrom().format(DATE_FMT)).append("</RazdobljeOd>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (z != null && z.getDateTo() != null)
        {
            sb.append("    <RazdobljeDo>").append(z.getDateTo().format(DATE_FMT)).append("</RazdobljeDo>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        sb.append("    <Obveznik>\n"); //$NON-NLS-1$
        sb.append("      <OIB>").append(esc(cleanOib)).append("</OIB>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("      <CarinskiUred>").append(esc(padCarinskiUred(z != null ? z.getTaxOfficeCode() : null))).append("</CarinskiUred>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("    </Obveznik>\n"); //$NON-NLS-1$

        sb.append("    <TipoviObveznika>\n"); //$NON-NLS-1$
        sb.append("      <TipObveznika>").append(esc(data.getTaxPayerCode())).append("</TipObveznika>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("    </TipoviObveznika>\n"); //$NON-NLS-1$

        sb.append("    <Stavke>\n"); //$NON-NLS-1$
        if (detalji != null)
        {
            for (MimvDetaljItem item : detalji)
            {
                appendVehicle(sb, item);
            }
        }
        sb.append("    </Stavke>\n"); //$NON-NLS-1$

        sb.append("    <UkIznosPPNova>").append(fmt2(totalNew)).append("</UkIznosPPNova>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("    <UkIznosPPRabljena>").append(fmt2(totalUsed)).append("</UkIznosPPRabljena>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("    <UkIznosPPNovaIRabljena>").append(fmt2(totalNew.add(totalUsed))).append("</UkIznosPPNovaIRabljena>\n"); //$NON-NLS-1$ //$NON-NLS-2$

        sb.append("  </MIMVObrazac>\n"); //$NON-NLS-1$
        sb.append("</ET405AA>"); //$NON-NLS-1$

        return sb.toString();
    }

    private static void appendVehicle(StringBuilder sb, MimvDetaljItem item)
    {
        sb.append("      <PodatakVozila>\n"); //$NON-NLS-1$

        // mandatory fields
        el(sb, "StatusVozila",         item.getStatusVozila()); //$NON-NLS-1$
        el(sb, "VrstaVozila",          item.getVrstaVozila()); //$NON-NLS-1$
        el(sb, "MarkaVozila",          item.getMarkaVozila()); //$NON-NLS-1$
        el(sb, "TipVarijantaTrgNaziv", item.getTipVarijantaTrgNaziv()); //$NON-NLS-1$
        el(sb, "VinOznaka",            item.getVinOznaka()); //$NON-NLS-1$

        // optional fields — omit when blank / null / zero
        elIfNotBlank(sb, "VrstaGoriva",          item.getVrstaGoriva()); //$NON-NLS-1$
        elDateIfNonZero(sb, "DatumPrveRegistracije", item.getDatumPrveRegistracije()); //$NON-NLS-1$
        elBigIntIfPositive(sb, "ProsjEmisijaCO2",    item.getProsjEmisijaCO2()); //$NON-NLS-1$
        elIfNotBlank(sb, "RazinaEmisije",            item.getRazinaEmisije()); //$NON-NLS-1$
        elBigIntIfPositive(sb, "RadniObujamMotora",  item.getRadniObujamMotora()); //$NON-NLS-1$
        elDecimalIfPositive(sb, "SnagaMotora",       item.getSnagaMotora(), 3); //$NON-NLS-1$
        elDecimalIfNotNull(sb,  "ProdajnaCijena",    item.getProdajnaCijena(), 2); //$NON-NLS-1$
        elDecimalIfPositive(sb, "BrojPrijedjenihKM", item.getBrojPrijedjenihKm(), 3); //$NON-NLS-1$
        elIfNotBlank(sb, "Kamper",    item.getKamper()); //$NON-NLS-1$
        elIntIfPositive(sb, "PlugIn", item.getPlugIn()); //$NON-NLS-1$
        elIfNotBlank(sb, "Vozilo71",  item.getVozilo71()); //$NON-NLS-1$
        elIfNotBlank(sb, "Vozilo81",  item.getVozilo81()); //$NON-NLS-1$
        elDecimalIfPositive(sb, "TestnoVozilo",  item.getTestnoVozilo(), 2); //$NON-NLS-1$
        elDecimalIfPositive(sb, "Deprecijacija", item.getDeprecijacija(), 2); //$NON-NLS-1$

        // mandatory fields (continued — must follow optional block per XSD sequence)
        el(sb, "PorezniObveznik", item.getPorezniObveznik()); //$NON-NLS-1$
        el(sb, "OIB",             item.getOib()); //$NON-NLS-1$
        el(sb, "BrRacuna",        item.getBrojRacuna()); //$NON-NLS-1$
        el(sb, "DatumIzdavanjaRacuna", intToDate(item.getDatumIzdavanjaRacuna())); //$NON-NLS-1$
        el(sb, "ObracunatiIznosPP",    fmt2(item.getObracunatiIznosPP())); //$NON-NLS-1$

        sb.append("      </PodatakVozila>\n"); //$NON-NLS-1$
    }

    // -------------------------------------------------------------------------
    // Element helpers
    // -------------------------------------------------------------------------

    private static void el(StringBuilder sb, String name, String value)
    {
        sb.append("        <").append(name).append(">") //$NON-NLS-1$ //$NON-NLS-2$
          .append(esc(value != null ? value : "")) //$NON-NLS-1$
          .append("</").append(name).append(">\n"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void elIfNotBlank(StringBuilder sb, String name, String value)
    {
        if (value != null && !value.isBlank())
        {
            el(sb, name, value);
        }
    }

    private static void elDateIfNonZero(StringBuilder sb, String name, Integer yyyymmdd)
    {
        if (yyyymmdd != null && yyyymmdd > 0)
        {
            el(sb, name, intToDate(yyyymmdd));
        }
    }

    /** Formats a BigDecimal as integer (Num_3_Tip / Num_7_Tip in XSD); omits element when <= 0. */
    private static void elBigIntIfPositive(StringBuilder sb, String name, BigDecimal value)
    {
        if (value != null && value.compareTo(BigDecimal.ZERO) > 0)
        {
            el(sb, name, String.valueOf(value.intValue()));
        }
    }

    private static void elIntIfPositive(StringBuilder sb, String name, Integer value)
    {
        if (value != null && value > 0)
        {
            el(sb, name, String.valueOf(value));
        }
    }

    private static void elDecimalIfPositive(StringBuilder sb, String name, BigDecimal value, int scale)
    {
        if (value != null && value.compareTo(BigDecimal.ZERO) > 0)
        {
            el(sb, name, String.format("%." + scale + "f", value)); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void elDecimalIfNotNull(StringBuilder sb, String name, BigDecimal value, int scale)
    {
        if (value != null)
        {
            el(sb, name, String.format("%." + scale + "f", value)); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private static String fmt2(BigDecimal value)
    {
        return value != null ? String.format("%.2f", value) : "0.00"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Converts YYYYMMDD integer to "YYYY-MM-DD" string required by xs:date.
     * Returns empty string for null or zero values.
     */
    private static String intToDate(Integer yyyymmdd)
    {
        if (yyyymmdd == null || yyyymmdd == 0)
        {
            return ""; //$NON-NLS-1$
        }
        String s = String.format("%08d", yyyymmdd); //$NON-NLS-1$
        return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Right-pads the tax office code with zeros to 6 characters as required by CarinskiUred_Tip (\d{6}).
     * Example: "0003" -> "000300".
     */
    private static String padCarinskiUred(String code)
    {
        if (code == null || code.isBlank())
        {
            return ""; //$NON-NLS-1$
        }
        String trimmed = code.trim();
        if (trimmed.length() >= 6)
        {
            return trimmed.substring(0, 6);
        }
        return String.format("%-6s", trimmed).replace(' ', '0'); //$NON-NLS-1$
    }

    /** Escapes XML special characters in text content. */
    private static String esc(String s)
    {
        if (s == null)
        {
            return ""; //$NON-NLS-1$
        }
        return s.replace("&", "&amp;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
