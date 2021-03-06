package com.mrkid.crawler.downloader;

import com.mrkid.crawler.CrawlerException;
import com.mrkid.crawler.Page;
import com.mrkid.crawler.Request;
import io.reactivex.Flowable;
import io.reactivex.processors.BehaviorProcessor;
import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * User: xudong
 * Date: 08/12/2016
 * Time: 7:12 PM
 */
@Data
public class HttpAsyncClientDownloader implements Downloader {

    private Logger logger = LoggerFactory.getLogger(HttpAsyncClientDownloader.class);

    private final CloseableHttpAsyncClient client;

    private final int overallTime;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private static <T> Flowable<T> toFlowable(CompletableFuture<T> future) {
        final BehaviorProcessor<T> processor = BehaviorProcessor.create();

        future.whenComplete((result, error) -> {
            if (error != null) {
                processor.onError(error);
            } else {
                processor.onNext(result);
                processor.onComplete();
            }
        });

        return processor;
    }


    @Override
    public Flowable<Page> download(Request request) {

        CompletableFuture<Page> promise = new CompletableFuture<>();

        HttpUriRequest httpUriRequest = null;
        switch (request.getMethod()) {
            case "GET":
                httpUriRequest = new HttpGet(request.getUrl());
                break;
            case "POST":
                final HttpPost httpPost = new HttpPost(request.getUrl());
                final List<BasicNameValuePair> nameValuePairs = request.getForm().entrySet().stream()
                        .map(e -> new BasicNameValuePair(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());

                try {
                    httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs, "utf-8"));
                } catch (UnsupportedEncodingException e) {
                    return Flowable.error(e);
                }
                httpUriRequest = httpPost;
                break;

            default:
                throw new UnsupportedOperationException(request.getMethod() + " is not supported");
        }

        logger.info("start downloading {} method {} form {}", request.getUrl(), request.getMethod(), request.getForm());
        Future<HttpResponse> future = client.execute(httpUriRequest, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse httpResponse) {
                final int status = httpResponse.getStatusLine().getStatusCode();
                logger.info("finish downloading {} method {} form {} status {}", request.getUrl(),
                        request.getMethod(), request.getForm(), status);


                try {
                    if (status != HttpStatus.SC_OK) {
                        promise.completeExceptionally(new CrawlerException(request.getUrl() + " return " + status));
                    } else {
                        final String value = IOUtils.toString(httpResponse.getEntity().getContent(), "utf-8");


                        Page page = new Page(request);
                        page.setRawText(value);

                        promise.complete(page);
                    }
                } catch (IOException e) {
                    promise.completeExceptionally(new CrawlerException(e));
                } finally {
                    try {
                        if (httpResponse != null) {
                            EntityUtils.consume(httpResponse.getEntity());
                        }
                    } catch (IOException e) {
                        logger.warn("close response fail", e);
                    }
                }

            }

            @Override
            public void failed(Exception e) {
                logger.info("fail downloading {} method {} form {} exception {}", request.getUrl(),
                        request.getMethod(), request.getForm(), e.getClass().getName() + ":" + e.getMessage());

                promise.completeExceptionally(new CrawlerException(e));
            }

            @Override
            public void cancelled() {
                promise.cancel(false);
            }
        });

        if (overallTime > 0) {
            scheduledExecutorService.schedule(() -> {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }, overallTime, TimeUnit.MILLISECONDS);
        }

        return toFlowable(promise);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
