package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request body for POST /mimv/form/build.
 * All fields except totals are needed to populate MIMV_ZAGLAVLJE.
 * Totals are computed from the inserted MIMV_DETALJ rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormBuildRequest {

    /** TIPOVI_OBVEZNIKA / SIFRA_OBRASCA_PP source (MV01, MV02, MV03) */
    private String taxPayerCode; //$NON-NLS-1$

    /** DATUM_PP — date of the form (YYYYMMDD) */
    private LocalDate formDate;

    /** REDNI_BROJ_PP — sequential number within the period */
    private Integer sequentialNumberInPeriod;

    /** REDNI_BROJ_PP_PROM — version/amendment number */
    private Integer versionNumber;

    /** RAZDOBLJE_OD — period start date */
    private LocalDate dateFrom;

    /** RAZDOBLJE_DO — period end date */
    private LocalDate dateTo;

    /** CARINSKI_URED — customs office code */
    private String taxOfficeCode; //$NON-NLS-1$

    /** CARINSKI_URED_OPIS — customs office description */
    private String taxOfficeDescription; //$NON-NLS-1$

    /** EMAIL_ADRESA */
    private String destinationEmail; //$NON-NLS-1$

    /** AKCIJA_PP — action code: N=new, A=amendment */
    private String actionCode; //$NON-NLS-1$

    /** NAZIV_OBVEZNIKA — company name */
    private String companyName; //$NON-NLS-1$

    /** SJEDISTE_OBVEZNIKA — company registered seat */
    private String companySeat; //$NON-NLS-1$

    /** ODGOVORNA_OSOBA — responsible person name */
    private String responsiblePerson; //$NON-NLS-1$
}
