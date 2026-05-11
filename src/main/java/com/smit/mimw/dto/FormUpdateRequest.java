package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /mimv/form/build/update.
 * All data needed to replace an existing MIMV form in Pantheon SQL tables
 * is provided inline — no AS400 reads are performed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormUpdateRequest
{
    /** The form header and detail rows to persist. */
    private FormUpdateData data;
}
