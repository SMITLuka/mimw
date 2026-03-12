package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * MI-MV obrazac – Mjesečno izvješće o obračunatim iznosima
 * posebnog poreza na motorna vozila.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MimvObrazac {

    // ===== Zaglavlje =====

    /** Naziv tvrtke */
    private String naziv;

    /** Sjedište */
    private String sjediste;

    /** OIB tvrtke */
    private String oib;

    /** Carinski ured */
    private String carinskiUred;

    /** Razdoblje – od (mjesec) */
    private Integer periodOd;

    /** Razdoblje – do (mjesec) */
    private Integer periodDo;

    /** Godina */
    private Integer godina;

    /** Tip obveznika: PROIZVODAC, TRGOVAC, REGISTRIRANI_TRGOVAC */
    private String tipObveznika;

    // ===== Stavke vozila =====

    /** Lista stavki (redaka tablice) */
    private List<VoziloStavka> stavke;

    // ===== Sumarni iznosi =====

    /** 25. Ukupno obračunati iznos posebnog poreza na nova vozila (kn) */
    private BigDecimal ukupnoNovaVozila;

    /** 26. Ukupno obračunati iznos posebnog poreza na rabljena vozila (kn) */
    private BigDecimal ukupnoRablenaVozila;

    /** 27. Ukupno obračunati iznos posebnog poreza na nova i rabljena vozila (kn) */
    private BigDecimal ukupnoSveVozila;

    // ===== Potvrda =====

    /** Datum potvrde */
    private String datumPotvrde;

    /** Ime i prezime odgovorne osobe */
    private String odgovornaOsoba;
}

