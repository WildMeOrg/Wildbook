package org.ecocean.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobilePackJobsTest {
    @TempDir Path directory;

    @Test void cachesRemoteAssetsWhoseLocalPathDoesNotExistYet() throws Exception {
        // URLAssetStore.localPath() names a /tmp file whether or not it has been fetched.
        Path missing = directory.resolve("not-yet-fetched.jpg");
        MediaAsset asset = mock(MediaAsset.class);
        when(asset.localPath()).thenReturn(missing);
        when(asset.cacheLocal()).thenAnswer(invocation -> { Files.write(missing, new byte[] {1}); return true; });
        assertEquals(missing, MobilePackJobs.localFile(asset));
        verify(asset).cacheLocal();
    }

    @Test void doesNotRefetchAssetsAlreadyOnDisk() throws Exception {
        Path present = Files.write(directory.resolve("present.jpg"), new byte[] {1});
        MediaAsset asset = mock(MediaAsset.class);
        when(asset.localPath()).thenReturn(present);
        assertEquals(present, MobilePackJobs.localFile(asset));
        verify(asset, never()).cacheLocal();
    }

    @Test void failsTheExportWhenNoLocalFileCanBeMaterialised() throws Exception {
        MediaAsset asset = mock(MediaAsset.class);
        when(asset.localPath()).thenReturn(directory.resolve("never.jpg"));
        when(asset.cacheLocal()).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> MobilePackJobs.localFile(asset));
        assertThrows(IllegalArgumentException.class, () -> MobilePackJobs.localFile(null));
    }
}
