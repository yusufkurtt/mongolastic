package com.kodcu.service;

import com.kodcu.config.ElasticConfiguration;
import com.kodcu.config.YamlConfiguration;
import com.kodcu.util.Constants;
import org.apache.log4j.Logger;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Hakan on 5/21/2015.
 */
public class BulkService {

    private final Logger logger = Logger.getLogger(BulkService.class);
    private final YamlConfiguration config;
    private final ElasticConfiguration client;

    public BulkService(final YamlConfiguration config, final ElasticConfiguration client) {
        this.config = config;
        this.client = client;
    }

    public void startBulkOperation() {
        // TODO: Hala geliştirime açık
        String file = config.getOutFile().concat(".json");
        Pattern pattern = Pattern.compile(Constants.CREATE_ACTION);

        //Delete index if existing
        this.deleteCurrentIndex(config.getDatabase());

        BulkProcessor bulkProcessor = BulkProcessor.builder(client.getClient(), new BulkProcessorListener())
                .setBulkActions(1000)
                .setBulkSize(new ByteSizeValue(1, ByteSizeUnit.GB))
                .build();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            logger.info("Transferring data began to elasticsearch.");
            String line;
            String indexName = config.getDatabase();
            String typeName = config.getCollection();
            String id = null;

            while ((line = br.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    id = matcher.group(1);
                } else {
                    IndexRequest indexRequest = new IndexRequest(indexName, typeName, id);
                    indexRequest.source(line.getBytes(Charset.forName("UTF-8")));
                    bulkProcessor.add(indexRequest);
                }
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex.fillInStackTrace());
        } finally {
            try {
                bulkProcessor.awaitClose(60, TimeUnit.MINUTES);
            } catch (InterruptedException ex) {
                logger.error(ex.getMessage(), ex.fillInStackTrace());
            } finally {
                client.closeNode();
            }
        }
    }

    public void deleteCurrentIndex(String indexName) {
        IndicesAdminClient admin = client.getClient().admin().indices();
        IndicesExistsRequestBuilder builder = admin.prepareExists(indexName);
        if (builder.execute().actionGet().isExists()) {
            DeleteIndexResponse delete = admin.delete(new DeleteIndexRequest(indexName)).actionGet();
            if (delete.isAcknowledged())
                logger.info("The current index was deleted.");
            else
                logger.info("The current index was not deleted.");
        }
    }

}
