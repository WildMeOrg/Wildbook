package org.ecocean.media;

import org.ecocean.mmutil.MediaUtilities;

public enum MediaAssetType {
    UNKNOWN(0),
    IMAGE(1),
    VIDEO(2),
    GPS(3);

    private int code;

    MediaAssetType(final int code) {
        this.code = code;
    }

    public static MediaAssetType fromCode(final int code) {
        for (MediaAssetType type : MediaAssetType.values()) {
            if (type.code == code) {
                return type;
            }
        }

        //
        // default to unknown
        //
        return MediaAssetType.UNKNOWN;
    }

    public static MediaAssetType fromFilename(final String path) {
        if (path == null) {
            return UNKNOWN;
        }

        if (MediaUtilities.isAcceptableImageFile(path)) {
            return IMAGE;
        }

        if (MediaUtilities.isAcceptableVideoFile(path)) {
            return VIDEO;
        }

/*
        if (MediaUtilities.isGpsFile(path)) {
            return GPS;
        }
*/

        return UNKNOWN;
    }

    public int getCode() {
        return code;
    }
}
