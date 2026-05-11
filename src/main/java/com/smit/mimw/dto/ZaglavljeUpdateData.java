package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Mutable display fields for the MIMV_ZAGLAVLJE header in the update flow.
 * Does not carry the form key (oibObveznika, datumPP, sifraObrascaPP, redniBrojPP, redniBrojPPProm)
 * or computed totals — those are resolved by the service and repository.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZaglavljeUpdateData
{
    /** AKCIJA_PP — N=new submission, A=amendment. Defaults to "N" when null. */
    private String actionCode;

    /** RAZDOBLJE_OD */
    private LocalDate dateFrom;

    /** RAZDOBLJE_DO */
    private LocalDate dateTo;

    /** CARINSKI_URED */
    private String taxOfficeCode;

    /** CARINSKI_URED_OPIS */
    private String taxOfficeDescription;

    /** NAZIV_OBVEZNIKA */
    private String companyDescription;

    /** SJEDISTE_OBVEZNIKA */
    private String companySeat;

    /** EMAIL_ADRESA */
    private String destinationEmail;

    /** ODGOVORNA_OSOBA */
    private String responsiblePerson;
}
