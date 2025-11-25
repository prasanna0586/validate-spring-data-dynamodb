package org.example.dynamodb.config;

import org.socialsignin.spring.data.dynamodb.core.TableNameResolver;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
@EnableDynamoDBRepositories(
        basePackages = "org.example.dynamodb.repository"
)
public class DynamoDbConfig {

    @Value("${aws.dynamodb.endpoint}")
    private String dynamoDbEndpoint;

    @Value("${aws.dynamodb.region}")
    private String awsRegion;

    @Value("${aws.dynamodb.accessKey}")
    private String awsAccessKey;

    @Value("${aws.dynamodb.secretKey}")
    private String awsSecretKey;

    @Value("${app.environment.prefix}")
    private String environmentPrefix;

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(dynamoDbEndpoint))
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
                .build();
    }

    @Bean
    public TableNameResolver tableNameResolver() {
        return new TableNameResolver() {
            @Override
            public <T> String resolveTableName(Class<T> domainClass, String baseTableName) {
                if (environmentPrefix == null || environmentPrefix.isEmpty()) {
                    return baseTableName;
                }
                return environmentPrefix + "_" + baseTableName;
            }
        };
    }
}
