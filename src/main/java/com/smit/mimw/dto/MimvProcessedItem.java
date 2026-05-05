package com.smit.mimw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single row from MIMV_ZAGLAVLJE — represents one previously submitted MIMV form.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MimvProcessedItem {

    /** IDENTIFIKATOR */
    private String identificator; //$NON-NLS-1$

    /** AKCIJA_PP — N=new, A=amendment */
    private String action; //$NON-NLS-1$

    /** RAZDOBLJE_OD */
    private LocalDate dateFrom;

    /** RAZDOBLJE_DO */
    private LocalDate dateTo;

    /** OIB_OBVEZNIKA */
    private String oibObveznika; //$NON-NLS-1$

    /** SIFRA_OBRASCA_PP — form type code (401, 405) */
    private String sifraObrascaPP; //$NON-NLS-1$

    /** REDNI_BROJ_PP */
    private Integer redniBrojPP;

    /** REDNI_BROJ_PP_PROM */
    private Integer redniBrojPPPromjena;

    /** NAZIV_OBVEZNIKA */
    private String nazivObveznika; //$NON-NLS-1$

    /** CARINSKI_URED */
    private String carinskiUred; //$NON-NLS-1$

    /** UKUP_IZNOS_NOVA */
    private BigDecimal ukupIznosNova;

    /** UKUP_IZNOS_RABLJENA */
    private BigDecimal ukupIznosRabljena;

    /** UKUP_IZNOS_NOVA_I_RAB */
    private BigDecimal ukupIznosNovaIRab;
}
