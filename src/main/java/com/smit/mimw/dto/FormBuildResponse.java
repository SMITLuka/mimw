package com.smit.mimw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response for POST /mimv/form/build.
 * Reflects the MIMV_ZAGLAVLJE row that was inserted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormBuildResponse {

    /** IDENTIFIKATOR — composite form identifier (oib-formDate-sifobr-seq-ver) */
    private String id; //$NON-NLS-1$

    /** OIB_OBVEZNIKA */
    private String oibObveznika; //$NON-NLS-1$

    /** RAZDOBLJE_OD */
    private LocalDate dateFrom;

    /** RAZDOBLJE_DO */
    private LocalDate dateTo;

    /** UKUP_IZNOS_NOVA — total calculated tax on new vehicles */
    private BigDecimal taxNewVehiclesSum;

    /** UKUP_IZNOS_RABLJENA — total calculated tax on used vehicles */
    private BigDecimal taxUsedVehiclesSum;

    /** UKUP_IZNOS_NOVA_I_RAB — combined total */
    private BigDecimal taxTotalSum;

    /** TIPOVI_OBVEZNIKA — taxpayer type code(s) */
    private String taxPayersTypeSelected; //$NON-NLS-1$

    /** NAZIV_OBVEZNIKA */
    private String companyDescription; //$NON-NLS-1$

    /** SJEDISTE_OBVEZNIKA */
    private String companySeat; //$NON-NLS-1$

    /** CARINSKI_URED */
    private String taxOfficeCode; //$NON-NLS-1$

    /** CARINSKI_URED_OPIS */
    private String taxOfficeDescription; //$NON-NLS-1$

    /** EMAIL_ADRESA */
    private String destinationEmail; //$NON-NLS-1$
}
