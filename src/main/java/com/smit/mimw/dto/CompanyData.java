package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Company identification data fetched from KB0D1.ZBEN1.
 * Used internally to pass company name and address into the form-build flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyData {

    /** BENNAME1 + " " + BENNAME2 */
    private String companyDescription;

    /** BENSTR + " " + BENPLZ + " " + BENORT */
    private String companySeat;
}
