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

    /** DATUM_KREIRANJA — when the form (this version) was created, ISO yyyy-MM-ddTHH:mm:ss; absent for older forms */
    private String createdAt; //$NON-NLS-1$

    /** DATUM_SLANJA — when the form was sent, ISO yyyy-MM-ddTHH:mm:ss; absent when it was not sent */
    private String sentAt; //$NON-NLS-1$

    /**
     * Status of the form:
     * {@value #STATUS_NOVI} (action N, not sent yet), {@value #STATUS_ISPRAVAK} (action A — a new version made
     * with "Kopiraj" — not sent yet), {@value #STATUS_POSLANO} (XML sent by e-mail, whatever the action).
     * NOVI and ISPRAVAK can be changed and deleted; POSLANO can only be corrected with a new version.
     */
    private String status; //$NON-NLS-1$

    public static final String STATUS_NOVI     = "NOVI"; //$NON-NLS-1$
    public static final String STATUS_ISPRAVAK = "ISPRAVAK"; //$NON-NLS-1$
    public static final String STATUS_POSLANO  = "POSLANO"; //$NON-NLS-1$

    /** Status from the sent time and the action code (AKCIJA_PP). */
    public static String statusOf(String sentAt, String action) {
        if (sentAt != null) {
            return STATUS_POSLANO;
        }
        return "A".equalsIgnoreCase(action) ? STATUS_ISPRAVAK : STATUS_NOVI; //$NON-NLS-1$
    }
}
