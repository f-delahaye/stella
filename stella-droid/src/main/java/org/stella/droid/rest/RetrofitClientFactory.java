package org.stella.droid.rest;

import okhttp3.*;
import retrofit2.Converter;
import retrofit2.Retrofit;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URL;

/**
 * A utility class which encapsulates all the retrofit stuff in one single place.
 * For better performance, the instance of retrofit is cached.
 */
public class RetrofitClientFactory {
    private static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080";

    static Retrofit retrofit = null;

    private String baseUrl;

    public RetrofitClientFactory() {
        this(DEFAULT_BASE_URL);
    }
    RetrofitClientFactory(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private Retrofit get() {
        if (retrofit == null) {
            Retrofit.Builder builder = new Retrofit.Builder().
                    baseUrl(baseUrl).
                    addConverterFactory(StringConverterFactory.INSTANCE);
            retrofit = builder.client(new OkHttpClient.Builder().build()).build();
        }
        return retrofit;
    }

    public <T> T createClient(Class<T> clientInterface) {
        return get().create(clientInterface);
    }

    private static class StringConverterFactory extends Converter.Factory {
        private static final StringConverterFactory INSTANCE = new StringConverterFactory();

        private static final MediaType MEDIA_TYPE = MediaType.parse("text/plain");


        @Override
        public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
            if (String.class.equals(type)) {
                return new Converter<ResponseBody, String>() {
                    @Override
                    public String convert(ResponseBody value) throws IOException {
                        return value.string();
                    }
                };
            }
            return null;
        }

        @Override
        public Converter<?, RequestBody> requestBodyConverter(Type type,
            Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {

            if (String.class.equals(type)) {
                return new Converter<String, RequestBody>() {
                    @Override
                    public RequestBody convert(String value) throws IOException {
                        return RequestBody.create(MEDIA_TYPE, value);
                    }
                };
            }
            return null;
        }
    }
}
