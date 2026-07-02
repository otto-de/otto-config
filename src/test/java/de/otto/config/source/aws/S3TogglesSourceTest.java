package de.otto.config.source.aws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.otto.config.domain.Toggles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

public class S3TogglesSourceTest {

    @Mock
    private S3Client s3Client;

    private S3TogglesSource source;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        source = S3TogglesSource.builder()
                                .s3Client(s3Client)
                                .bucketName("service-bucket")
                                .togglesFolder("feature-toggles/")
                                .build();
    }

    private void mockObjects(String... keys) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket("service-bucket")
                .prefix("feature-toggles/")
                .build();
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(Arrays.stream(keys)
                                .map(k -> S3Object.builder().key(k).build())
                                .collect(Collectors.toList()))
                .build();
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenReturn(new ListObjectsV2Iterable(s3Client, request));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);
    }

    @Test
    public void shouldOrMergeDuplicateToggleNames() {
        mockObjects("feature-toggles/off.featureA", "feature-toggles/on.featureA");
        Toggles toggles = source.getOrLoad();
        assertThat(toggles.getProperties(), hasEntry("featureA", true));
    }

    @Test
    public void shouldHandleMultipleToggles() {
        mockObjects("feature-toggles/on.featureA", "feature-toggles/off.featureB");
        Toggles toggles = source.getOrLoad();
        assertThat(toggles.getProperties(), aMapWithSize(2));
        assertThat(toggles.getProperties(), hasEntry("featureA", true));
        assertThat(toggles.getProperties(), hasEntry("featureB", false));
    }

    @Test
    public void shouldReturnEmptyTogglesWhenBucketEmpty() {
        mockObjects();
        Toggles toggles = source.getOrLoad();
        assertThat(toggles.getProperties().isEmpty(), is(true));
    }

    @Test
    public void shouldReturnEmptyTogglesWhenResponseHasNoContents() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket("service-bucket")
                .prefix("feature-toggles/")
                .build();
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenReturn(new ListObjectsV2Iterable(s3Client, request));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().build());
        Toggles toggles = source.getOrLoad();
        assertThat(toggles.getProperties().isEmpty(), is(true));
    }

    @Test
    public void shouldNormalizeFolderWithoutTrailingSlashToAFolderPrefix() {
        ArgumentCaptor<ListObjectsV2Request> requestCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(S3Object.builder().key("feature-toggles/on.featureA").build())
                .build();
        S3TogglesSource noSlashSource = S3TogglesSource.builder()
                .s3Client(s3Client)
                .bucketName("service-bucket")
                .togglesFolder("feature-toggles")
                .build();
        when(s3Client.listObjectsV2Paginator(requestCaptor.capture()))
                .thenReturn(new ListObjectsV2Iterable(s3Client, ListObjectsV2Request.builder()
                                                                                    .bucket("service-bucket")
                                                                                    .prefix("feature-toggles/")
                                                                                    .build()));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        Toggles toggles = noSlashSource.getOrLoad();

        assertThat(requestCaptor.getValue().prefix(), is("feature-toggles/"));
        assertThat(toggles.getProperties(), hasEntry("featureA", true));
    }

    @Test
    public void shouldNotDoubleAppendSlashWhenFolderAlreadyEndsWithSlash() {
        ArgumentCaptor<ListObjectsV2Request> requestCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        when(s3Client.listObjectsV2Paginator(requestCaptor.capture()))
                .thenReturn(new ListObjectsV2Iterable(s3Client, ListObjectsV2Request.builder()
                                                                                    .bucket("service-bucket")
                                                                                    .prefix("feature-toggles/")
                                                                                    .build()));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().build());

        source.getOrLoad();

        assertThat(requestCaptor.getValue().prefix(), is("feature-toggles/"));
    }

    @Test
    public void shouldMergeTogglesAcrossPaginatedResults() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket("service-bucket")
                .prefix("feature-toggles/")
                .build();
        ListObjectsV2Response page1 = ListObjectsV2Response.builder()
                .isTruncated(true)
                .nextContinuationToken("t")
                .contents(S3Object.builder().key("feature-toggles/on.featureA").build())
                .build();
        ListObjectsV2Response page2 = ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(S3Object.builder().key("feature-toggles/off.featureB").build())
                .build();
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenReturn(new ListObjectsV2Iterable(s3Client, request));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page1, page2);

        Toggles toggles = source.getOrLoad();

        assertThat(toggles.getProperties(), aMapWithSize(2));
        assertThat(toggles.getProperties(), hasEntry("featureA", true));
        assertThat(toggles.getProperties(), hasEntry("featureB", false));
    }
}
