package org.jetbrains.teamcity.hire.test;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.teamcity.hire.test.filesystem.api.FileSystemsManager;
import org.jetbrains.teamcity.hire.test.filesystem.impl.FileSystemsManagerImpl;
import org.junit.jupiter.api.BeforeAll;

public class RootTest {

    static final String PROJECT_FOLDER = "c:\\Hd9ejPOsfn7Q\\";

    static Path fileSystemPath;
    static FileSystemsManager fileSystemsManager;

    @BeforeAll
    private static void beforeAll() {
        fileSystemPath = Paths.get(PROJECT_FOLDER, "first.fs");
        fileSystemsManager = new FileSystemsManagerImpl();
    }

}
