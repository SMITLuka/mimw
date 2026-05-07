package com.smit.mimw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Single row from MIMV_DETALJ — represents one vehicle detail line on a MIMV form.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MimvDetaljItem {

    /** OIB_OBVEZNIKA */
    private String oibObveznika;

    /** DATUM_PP — form date as YYYYMMDD integer */
    private Integer datumPP;

    /** SIFRA_OBRASCA_PP */
    private String sifraObrascaPP;

    /** REDNI_BROJ_PP */
    private Integer redniBrojPP;

    /** REDNI_BROJ_PP_PROM */
    private Integer redniBrojPPProm;

    /** SIFRA_VOZILA */
    private String sifraVozila;

    /** STATUS_VOZILA — N=new, R=used, NT=other used */
    private String statusVozila;

    /** VRSTA_VOZILA */
    private String vrstaVozila;

    /** MARKA_VOZILA */
    private String markaVozila;

    /** TIP_VARIJANTA_TRG_NAZIV */
    private String tipVarijantaTrgNaziv;

    /** VIN_OZNAKA */
    private String vinOznaka;

    /** VRSTA_GORIVA */
    private String vrstaGoriva;

    /** DATUM_PRVE_REGISTRACIJE — YYYYMMDD integer */
    private Integer datumPrveRegistracije;

    /** PROSJ_EMISIJA_CO2 */
    private BigDecimal prosjEmisijaCO2;

    /** RAZINA_EMISIJE */
    private String razinaEmisije;

    /** RADNI_OBUJAM_MOTORA */
    private BigDecimal radniObujamMotora;

    /** SNAGA_MOTORA */
    private BigDecimal snagaMotora;

    /** PRODAJNA_CIJENA */
    private BigDecimal prodajnaCijena;

    /** BROJ_PRIJEDJENIH_KM */
    private BigDecimal brojPrijedjenihKm;

    /** KAMPER */
    private String kamper;

    /** PLUG_IN */
    private Integer plugIn;

    /** VOZILO_71 */
    private String vozilo71;

    /** VOZILO_81 */
    private String vozilo81;

    /** TESTNO_VOZILO */
    private BigDecimal testnoVozilo;

    /** DEPRECIJACIJA */
    private BigDecimal deprecijacija;

    /** POREZNI_OBVEZNIK */
    private String porezniObveznik;

    /** OIB — OIB of the buyer/taxpayer */
    private String oib;

    /** BROJ_RACUNA */
    private String brojRacuna;

    /** DATUM_IZDAVANJA_RACUNA — YYYYMMDD integer */
    private Integer datumIzdavanjaRacuna;

    /** OBRACUNATI_IZNOS_PP */
    private BigDecimal obracunatiIznosPP;
}
