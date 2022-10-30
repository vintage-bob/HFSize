import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

public class HFSizeTest {

    @Test
    @Ignore
    public void image() throws IOException {
        final String payload = "file.sit";

        HFSize.main(new String[] {payload});
    }
}
