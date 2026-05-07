package com.smit.mimw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Represents a single MIMV_ZAGLAVLJE header row.
 * Returned as the zaglavlje field in the POST /mimv/form/build response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MimvZaglavljeItem {

    /** IDENTIFIKATOR — composite form identifier (oib-formDate-sifobr-seq-ver) */
    private String id;

    /** OIB_OBVEZNIKA */
    private String oibObveznika;

    /** DATUM_PP — form date as YYYYMMDD integer */
    private Integer datumPP;

    /** SIFRA_OBRASCA_PP — 401 or 405 */
    private String sifraObrascaPP;

    /** REDNI_BROJ_PP */
    private Integer redniBrojPP;

    /** REDNI_BROJ_PP_PROM */
    private Integer redniBrojPPProm;

    /** AKCIJA_PP — N=new, A=amendment */
    private String actionCode;

    /** RAZDOBLJE_OD */
    private LocalDate dateFrom;

    /** RAZDOBLJE_DO */
    private LocalDate dateTo;

    /** CARINSKI_URED */
    private String taxOfficeCode;

    /** CARINSKI_URED_OPIS */
    private String taxOfficeDescription;

    /** NAZIV_OBVEZNIKA — fetched from KB0D1.ZBEN1 */
    private String companyDescription;

    /** SJEDISTE_OBVEZNIKA — fetched from KB0D1.ZBEN1 */
    private String companySeat;

    /** EMAIL_ADRESA */
    private String destinationEmail;

    /** ODGOVORNA_OSOBA */
    private String responsiblePerson;

    /** TIPOVI_OBVEZNIKA */
    private String taxPayerCode;

    /** UKUP_IZNOS_NOVA */
    private BigDecimal taxNewVehiclesSum;

    /** UKUP_IZNOS_RABLJENA */
    private BigDecimal taxUsedVehiclesSum;

    /** UKUP_IZNOS_NOVA_I_RAB */
    private BigDecimal taxTotalSum;

    /** Active taxpayer type codes from MIMV_ODABRANI_TIPOVI_OBVEZNIKA (MV01, MV02, MV03) */
    private List<String> selectedTaxPayerTypes;
}
