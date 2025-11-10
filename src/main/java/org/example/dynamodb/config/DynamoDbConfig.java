package org.example.dynamodb.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableDynamoDBRepositories(
        basePackages = "org.example.dynamodb.repository",
        dynamoDBMapperConfigRef = "dynamoDBMapperConfig"
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
    public AmazonDynamoDB amazonDynamoDB() {
        return AmazonDynamoDBClientBuilder
                .standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(dynamoDbEndpoint, awsRegion))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(awsAccessKey, awsSecretKey)))
                .build();
    }

    /**
     * Configures DynamoDB mapper with table name prefix based on environment.
     * This allows using the same code across multiple environments (dev, staging, prod)
     * with different table names (e.g., dev-DocumentMetadata, prod-DocumentMetadata).
     *
     * @return DynamoDBMapperConfig with environment-specific table name prefix
     */
    @Bean
    public DynamoDBMapperConfig dynamoDBMapperConfig() {
        // Create a TableNameOverride with our prefix
        DynamoDBMapperConfig.TableNameOverride override =
                DynamoDBMapperConfig.TableNameOverride.withTableNamePrefix(environmentPrefix + "-");

        return new DynamoDBMapperConfig.Builder()
                .withTableNameOverride(override)
                .build();
    }
}
