package org.jetbrains.teamcity.hire.test;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;

public class RootTest {

    static final String PROJECT_FOLDER = "c:\\Hd9ejPOsfn7Q\\";

    static Path fileSystemPath;

    @BeforeAll
    private static void beforeAll() {
        fileSystemPath = Paths.get(PROJECT_FOLDER, "first.fs");
    }

}
