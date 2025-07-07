package com.catas.wicked.common.bean.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OutputMessage implements Message {

    public enum Source {
        /**
         * not output data, just ignore
         */
        IGNORE,

        /**
         * output request content
         */
        REQ_CONTENT,

        /**
         * output response content
         */
        RESP_CONTENT,
        ;
    }

    private String requestId;

    private Source source;

    private File targetFile;
}
