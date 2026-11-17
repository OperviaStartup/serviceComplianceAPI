ALTER TABLE obligation_evidence_requirements
    DROP CONSTRAINT obligation_evidence_requirements_source_requirement_id_fkey;
ALTER TABLE obligation_evidence_requirements
    ALTER COLUMN source_requirement_id SET NOT NULL;

ALTER TABLE evidences ADD CONSTRAINT ck_required_evidence_payload CHECK (
    evidence_requirement_id IS NULL OR
    (created_by IS NOT NULL AND captured_at IS NOT NULL AND file_size IS NOT NULL
        AND checksum IS NOT NULL AND checksum ~ '^[0-9a-f]{64}$' AND (
        (type = 'PHOTO' AND storage_key IS NOT NULL AND content_type IS NOT NULL
            AND content_type IN ('image/jpeg','image/png') AND file_size BETWEEN 1 AND 5242880
            AND text_value IS NULL) OR
        (type = 'SIGNATURE' AND storage_key IS NOT NULL AND content_type = 'image/png'
            AND file_size BETWEEN 1 AND 1048576 AND text_value IS NULL) OR
        (type = 'QR_CODE' AND storage_key IS NULL AND text_value IS NOT NULL
            AND file_size > 0 AND length(text_value) BETWEEN 1 AND 512
            AND latitude IS NULL AND longitude IS NULL AND accuracy_meters IS NULL) OR
        (type = 'LOCATION' AND storage_key IS NULL AND text_value IS NULL AND file_size > 0
            AND latitude IS NOT NULL AND longitude IS NOT NULL AND accuracy_meters IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
            AND accuracy_meters > 0)
    ))
);
