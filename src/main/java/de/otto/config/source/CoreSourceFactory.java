package de.otto.config.source;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.client.hashicorp.VaultClient;
import de.otto.config.client.hashicorp.auth.VaultAuthenticatorFactory;
import de.otto.config.core.Configuration;
import de.otto.config.core.Context;
import de.otto.config.core.source.Source;
import de.otto.config.core.source.SourceCreator;
import de.otto.config.core.source.SourceFactory;
import de.otto.config.domain.Properties;
import de.otto.config.domain.Toggles;
import de.otto.config.source.aws.AppConfigSource;
import de.otto.config.source.aws.S3TogglesSource;
import de.otto.config.source.aws.SecretsManagerSource;
import de.otto.config.source.aws.SsmSource;
import de.otto.config.source.file.FileSource;
import de.otto.config.source.hashicorp.VaultSource;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

@Slf4j
public class CoreSourceFactory implements SourceFactory {
    private static final Set<String> LOCAL_PROFILES = Set.of("local", "test", "integration-test");
    private static final String LOCAL_FILE_SOURCE = "properties.json";

    @SourceCreator("aws.s3.toggles")
    public static Source<Toggles> createS3TogglesSource(Context context) {
        if (isLocalProfile(context.getProfile())) {
            return createFileSource(context, Toggles.empty, Toggles.typeReference, "toggles");
        }

        return S3TogglesSource.builder()
                              .s3Client(context.getClientRegistry().registerIfAbsent(S3Client.class,
                                                                                     () -> S3Client.builder().build()))
                              .bucketName(context.getConfiguration().getValue("otto.config.aws.s3.toggles.bucket.name"))
                              .togglesFolder(context.getConfiguration().getValue("otto.config.aws.s3.toggles.folder.name"))
                              .build();
    }

    @SourceCreator("aws.appconfig.toggles")
    public static Source<Toggles> createTogglesSource(Context context) {
        return createAppConfigSource(context, 
                                     "toggles", 
                                     Toggles.empty, 
                                     Toggles.typeReference, 
                                     "toggles");
    }

    @SourceCreator("aws.appconfig.properties")
    public static Source<Properties> createPropertiesSource(Context context) {
        return createAppConfigSource(context, 
                                     "properties", 
                                     Properties.empty, 
                                     Properties.typeReference);
    }

    @SourceCreator("aws.secrets")
    public static Source<Properties> createSecretsManagerSource(Context context) {
        if (isLocalProfile(context.getProfile())) {
            return createFileSource(context);
        }

        String secretArn = context.getConfiguration().getValue("otto.config.aws.secrets.arn");
        List<PropertySource> sources = Arrays.stream(secretArn.split(",")).map(arn -> {
            return SecretsManagerSource.builder()
                                       .secretARN(arn)
                                       .secretsManagerClient(context.getClientRegistry().registerIfAbsent(SecretsManagerClient.class,
                                                                                                         () -> SecretsManagerClient.builder().build()))
                                       .objectMapper(context.getClientRegistry().get(ObjectMapper.class))
                                       .isPullRefreshEnabled(isPullRefreshEnabled(context))
                                       .build();
        }).collect(Collectors.toList());
        return CombinedPropertySource.builder().sources(sources).build();
    }

    @SourceCreator("aws.ssm")
    public static Source<Properties> createSsmSource(Context context) {
        if (isLocalProfile(context.getProfile())) {
            return createFileSource(context);
        }

        String ssmPath = context.getConfiguration().getValue("otto.config.aws.ssm.path.prefix", "/");
        List<PropertySource> sources = Arrays.stream(ssmPath.split(",")).map(path -> {
            return SsmSource.builder()
                            .applicationIdentifier(context.getAppName())
                            .ssmClient(context.getClientRegistry().registerIfAbsent(SsmClient.class,
                                                                                    () -> SsmClient.builder().build()))
                            .ssmPathPrefix(path)
                            .isPullRefreshEnabled(isPullRefreshEnabled(context))
                            .build();
        }).collect(Collectors.toList());
        return CombinedPropertySource.builder().sources(sources).build();
    }

    @SourceCreator("hashicorp.vault")
    public static Source<Properties> createVaultSource(Context context) {
        if (isLocalProfile(context.getProfile())) {
            return createFileSource(context);
        }   

        String secretPath = context.getConfiguration().getValue("otto.config.hashicorp.vault.path");
        List<PropertySource> sources = Arrays.stream(secretPath.split(",")).map(path -> {
            return VaultSource.builder()
                              .vaultClient(context.getClientRegistry().registerIfAbsent(VaultClient.class,
                                                                                        () -> VaultClient.builder()
                                                                                                         .url(context.getConfiguration().getValue("otto.config.hashicorp.vault.url"))
                                                                                                         .vaultAuthenticator(VaultAuthenticatorFactory.create(context))
                                                                                                         .objectMapper(context.getClientRegistry().get(ObjectMapper.class))
                                                                                                         .build()))
                              .secretPath(path)
                              .previousVersions(context.getConfiguration().getValueAsInt("otto.config.hashicorp.vault.prev.versions", 1))
                              .build();
        }).collect(Collectors.toList());
        return CombinedPropertySource.builder().sources(sources).build();
    }

    public static boolean isLocalProfile(String profile) {
        return (profile == null || LOCAL_PROFILES.contains(profile.toLowerCase())) && isLocalFileSourceAvailable();
    }

    public static boolean isLocalFileSourceAvailable() {
        return Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(LOCAL_FILE_SOURCE) != null;

    }

    public static Source<Properties> createFileSource(Context context) {
        return createFileSource(context, 
                                LOCAL_FILE_SOURCE, 
                                Properties.empty, 
                                Properties.typeReference, 
                                "");
    }

    public static <T extends Configuration<?>> Source<T> createFileSource(Context context, T emptyValue, TypeReference<T> typeReference) {
        return createFileSource(context, 
                                LOCAL_FILE_SOURCE, 
                                emptyValue, 
                                typeReference, 
                                "");
    }

    public static <T extends Configuration<?>> Source<T> createFileSource(Context context, T emptyValue, TypeReference<T> typeReference, String section) {
        return createFileSource(context, 
                                LOCAL_FILE_SOURCE, 
                                emptyValue, 
                                typeReference, 
                                section);
    }

    public static <T extends Configuration<?>> Source<T> createFileSource(Context context, String localFile, T emptyValue, TypeReference<T> typeReference, String section) {
        return FileSource.<T>builder()
                         .objectMapper(context.getClientRegistry().get(ObjectMapper.class))
                         .localFile(localFile)
                         .emptyValue(emptyValue)
                         .typeReference(typeReference)
                         .section(section)
                         .build();
    }

    public static <T extends Configuration<?>> Source<T> createAppConfigSource(Context context, String configurationProfileIdentifier, T emptyValue, TypeReference<T> typeReference) {
        return createAppConfigSource(context, 
                                     configurationProfileIdentifier, 
                                     emptyValue, 
                                     typeReference, 
                                     null);
    }

    public static <T extends Configuration<?>> Source<T> createAppConfigSource(Context context, String configurationProfileIdentifier, T emptyValue, TypeReference<T> typeReference, String section) {
        if (isLocalProfile(context.getProfile())) {
            return createFileSource(context, 
                                    emptyValue, 
                                    typeReference, 
                                    section);
        }
                                                                                               
        return AppConfigSource.<T>builder()
                              .applicationIdentifier(context.getAppName())
                              .configurationProfileIdentifier(configurationProfileIdentifier)
                              .appConfigDataClient(context.getClientRegistry().registerIfAbsent(AppConfigDataClient.class,
                                                                                                () -> AppConfigDataClient.builder().build()))
                              .objectMapper(context.getClientRegistry().get(ObjectMapper.class))
                              .emptyValue(emptyValue)
                              .typeReference(typeReference)
                              .isPullRefreshEnabled(isPullRefreshEnabled(context))
                              .build();
    }

    private static boolean isPullRefreshEnabled(Context context) {
        return !context.getConfiguration().getValueAsBoolean("otto.config.aws.change.notifications.enabled", false);
    }
}
