package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO that maps to a single row of IVAS0000B0.KLCFCPP (MVMZP Zaglavlje — MI-MV form header).
 *
 * Column mapping (AS400 system name → human-readable description):
 *   CFYQAA  — SifraObrascaPP       (form type code, e.g. 401, 405)
 *   CFYPAA  — OIBObveznika          (taxpayer OIB)
 *   CFRIDX  — SifraKorisnika        (user code)
 *   CFNSRO  — SifraPoduzeća         (company code, 6-char padded, e.g. "000080")
 *   CFMSTS  — StatusSloga           (record status)
 *   CFLTAQ  — CarinskiUredOpis      (customs office description)
 *   CFLSAQ  — Identifikator         (unique form identifier, e.g. "56770551293-01072014-401-01-001")
 *   CFLRAQ  — AkcijaPP              (action code: N=new, A=amended)
 *   CFLQAQ  — EmailAdresa           (email address)
 *   CFLPAQ  — OdgovornaOsoba        (responsible person)
 *   CFLOAQ  — SjedisteObveznika     (taxpayer registered seat)
 *   CFLNAQ  — NazivObveznika        (taxpayer name)
 *   CFLMAQ  — CarinskiUred          (customs office code, e.g. "030147")
 *   CFKRAQ  — TekstDodatni2         (additional text 2)
 *   CFKQAQ  — TekstDodatni1         (additional text 1)
 *   CFIZAU  — IznosDodatni2         (additional amount 2)
 *   CFIYAU  — IznosDodatni1         (additional amount 1)
 *   CFI6AG  — UkIznosUplacenogPP    (total paid PP amount)
 *   CFI5AG  — UkIznosPP             (total PP amount)
 *   CFI4AG  — RazdobljeDo           (period end, YYYYMMDD as integer)
 *   CFI3AG  — RazdobljeOd           (period start, YYYYMMDD as integer)
 *   CFI2AG  — RedniBrojPPPromjena   (sequential change number)
 *   CFI1AG  — RedniBrojPP           (sequential PP number)
 *   CFI0AG  — DatumPP               (PP date, YYYYMMDD as integer)
 *   CFFSDG  — DatumDodatni2         (additional date 2)
 *   CFFRDG  — DatumDodatni1         (additional date 1)
 *   CFB4SB  — StatusSloga2          (record status 2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlcfcppRecord {

    private String sifraObrascaPP;       // CFYQAA
    private String oibObveznika;         // CFYPAA
    private String sifraKorisnika;       // CFRIDX
    private String sifraPoduzecea;       // CFNSRO
    private String statusSloga;          // CFMSTS
    private String carinskiUredOpis;     // CFLTAQ
    private String identifikator;        // CFLSAQ
    private String akcijaPP;             // CFLRAQ
    private String emailAdresa;          // CFLQAQ
    private String odgovornaOsoba;       // CFLPAQ
    private String sjedisteObveznika;    // CFLOAQ
    private String nazivObveznika;       // CFLNAQ
    private String carinskiUred;         // CFLMAQ
    private String tekstDodatni2;        // CFKRAQ
    private String tekstDodatni1;        // CFKQAQ
    private BigDecimal iznosDodatni2;    // CFIZAU
    private BigDecimal iznosDodatni1;    // CFIYAU
    private BigDecimal ukIznosUplacenogPP; // CFI6AG
    private BigDecimal ukIznosPP;        // CFI5AG
    private Integer razdobljeDo;         // CFI4AG  (YYYYMMDD)
    private Integer razdobljeOd;         // CFI3AG  (YYYYMMDD)
    private Integer redniBrojPPPromjena; // CFI2AG
    private Integer redniBrojPP;         // CFI1AG
    private Integer datumPP;             // CFI0AG  (YYYYMMDD)
    private BigDecimal datumDodatni2;    // CFFSDG
    private BigDecimal datumDodatni1;    // CFFRDG
    private String statusSloga2;         // CFB4SB
}

