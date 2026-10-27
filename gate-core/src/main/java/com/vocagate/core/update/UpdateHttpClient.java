package com.vocagate.core.update;

import java.io.IOException;

public interface UpdateHttpClient {
    byte[] get(String url) throws IOException;
}
