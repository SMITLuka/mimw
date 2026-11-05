package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /mimv/form/delete.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormDeleteRequest {

    /** IDENTIFIKATOR of the form, as returned by GET /mimv/preview/existing */
    private String identificator; //$NON-NLS-1$
}
