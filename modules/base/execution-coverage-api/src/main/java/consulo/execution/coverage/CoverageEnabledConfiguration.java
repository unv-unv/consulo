package consulo.execution.coverage;

import consulo.application.Application;
import consulo.container.boot.ContainerPathManager;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizable;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.io.File;

/**
 * Base class for run configurations with enabled code coverage
 *
 * @author ven
 */
public abstract class CoverageEnabledConfiguration implements JDOMExternalizable {
    private static final Logger LOG = Logger.getInstance(CoverageEnabledConfiguration.class);

    public static final Key<CoverageEnabledConfiguration> COVERAGE_KEY = Key.create("consulo.ide.impl.idea.coverage");

    protected static final String COVERAGE_ENABLED_ATTRIBUTE_NAME = "enabled";
    protected static final String COVERAGE_RUNNER = "runner";
    protected static final String TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME = "per_test_coverage_enabled";
    protected static final String SAMPLING_COVERAGE_ATTRIBUTE_NAME = "sample_coverage";
    protected static final String TRACK_TEST_FOLDERS = "track_test_folders";

    private final Project myProject;
    private final RunConfigurationBase myConfiguration;

    private boolean myIsCoverageEnabled = false;
    private String myRunnerId;
    private CoverageRunner myCoverageRunner;
    private boolean myTrackPerTestCoverage = true;
    private boolean mySampling = true;
    private boolean myTrackTestFolders = false;

    protected String myCoverageFilePath;
    private CoverageSuite myCurrentCoverageSuite;

    public CoverageEnabledConfiguration(RunConfigurationBase configuration) {
        myConfiguration = configuration;
        myProject = configuration.getProject();
    }

    public RunConfigurationBase getConfiguration() {
        return myConfiguration;
    }

    public boolean isCoverageEnabled() {
        return myIsCoverageEnabled;
    }

    public void setCoverageEnabled(boolean isCoverageEnabled) {
        myIsCoverageEnabled = isCoverageEnabled;
    }

    public boolean isSampling() {
        return mySampling;
    }

    public void setSampling(boolean sampling) {
        mySampling = sampling;
    }

    public String getRunnerId() {
        return myRunnerId;
    }

    @Nullable
    public CoverageRunner getCoverageRunner() {
        return myCoverageRunner;
    }

    public void setCoverageRunner(@Nullable CoverageRunner coverageRunner) {
        myCoverageRunner = coverageRunner;
        myRunnerId = coverageRunner != null ? coverageRunner.getId() : null;
        myCoverageFilePath = null;
    }

    public boolean isTrackPerTestCoverage() {
        return myTrackPerTestCoverage;
    }

    public void setTrackPerTestCoverage(boolean collectLineInfo) {
        myTrackPerTestCoverage = collectLineInfo;
    }

    public boolean isTrackTestFolders() {
        return myTrackTestFolders;
    }

    public void setTrackTestFolders(boolean trackTestFolders) {
        myTrackTestFolders = trackTestFolders;
    }

    public CoverageSuite getCurrentCoverageSuite() {
        return myCurrentCoverageSuite;
    }

    public void setCurrentCoverageSuite(CoverageSuite currentCoverageSuite) {
        myCurrentCoverageSuite = currentCoverageSuite;
    }

    public String getName() {
        return myConfiguration.getName();
    }

    public boolean canHavePerTestCoverage() {
        return Application.get().getExtensionPoint(CoverageEngine.class)
            .anyMatchSafe(engine -> engine.isApplicableTo(myConfiguration) && engine.canHavePerTestCoverage(myConfiguration));
    }


    public static boolean isApplicableTo(@Nonnull RunConfigurationBase runConfiguration) {
        CoverageEnabledConfiguration configuration = runConfiguration.getCopyableUserData(COVERAGE_KEY);
        //noinspection SimplifiableIfStatement
        if (configuration != null) {
            return true;
        }

        return Application.get().getExtensionPoint(CoverageEngine.class)
            .anyMatchSafe(engine -> engine.isApplicableTo(runConfiguration));
    }

    @Nonnull
    public static CoverageEnabledConfiguration getOrCreate(@Nonnull RunConfigurationBase runConfiguration) {
        CoverageEnabledConfiguration configuration = runConfiguration.getCopyableUserData(COVERAGE_KEY);
        if (configuration == null) {
            configuration = Application.get().getExtensionPoint(CoverageEngine.class).computeSafeIfAny(
                engine -> engine.isApplicableTo(runConfiguration)
                    ? engine.createCoverageEnabledConfiguration(runConfiguration)
                    : null
            );
            LOG.assertTrue(
                configuration != null,
                "Coverage enabled run configuration wasn't found for run configuration: " + runConfiguration.getName() +
                    ", type = " + runConfiguration.getClass().getName()
            );
            runConfiguration.putCopyableUserData(COVERAGE_KEY, configuration);
        }
        return configuration;
    }

    @Nullable
    public String getCoverageFilePath() {
        if (myCoverageFilePath == null) {
            myCoverageFilePath = createCoverageFile();
        }
        return myCoverageFilePath;
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        // is enabled
        String coverageEnabledValueStr = element.getAttributeValue(COVERAGE_ENABLED_ATTRIBUTE_NAME);
        myIsCoverageEnabled = Boolean.valueOf(coverageEnabledValueStr);

        // track per test coverage
        String collectLineInfoAttribute = element.getAttributeValue(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME);
        myTrackPerTestCoverage = collectLineInfoAttribute == null || Boolean.valueOf(collectLineInfoAttribute);

        // sampling
        String sampling = element.getAttributeValue(SAMPLING_COVERAGE_ATTRIBUTE_NAME);
        mySampling = sampling != null && Boolean.valueOf(sampling);

        // track test folders
        String trackTestFolders = element.getAttributeValue(TRACK_TEST_FOLDERS);
        myTrackTestFolders = trackTestFolders != null && Boolean.valueOf(trackTestFolders);

        // coverage runner
        String runnerId = element.getAttributeValue(COVERAGE_RUNNER);
        if (runnerId != null) {
            myRunnerId = runnerId;
            myCoverageRunner = Application.get().getExtensionPoint(CoverageRunner.class)
                .findFirstSafe(coverageRunner -> myRunnerId.equals(coverageRunner.getId()));
        }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        // enabled
        element.setAttribute(COVERAGE_ENABLED_ATTRIBUTE_NAME, String.valueOf(myIsCoverageEnabled));

        // per test
        if (!myTrackPerTestCoverage) {
            element.setAttribute(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME, String.valueOf(myTrackPerTestCoverage));
        }

        // sampling
        if (mySampling) {
            element.setAttribute(SAMPLING_COVERAGE_ATTRIBUTE_NAME, String.valueOf(mySampling));
        }

        // test folders
        if (myTrackTestFolders) {
            element.setAttribute(TRACK_TEST_FOLDERS, String.valueOf(myTrackTestFolders));
        }

        // runner
        if (myCoverageRunner != null) {
            element.setAttribute(COVERAGE_RUNNER, myCoverageRunner.getId());
        }
        else if (myRunnerId != null) {
            element.setAttribute(COVERAGE_RUNNER, myRunnerId);
        }
    }

    @Nullable
    protected String createCoverageFile() {
        if (myCoverageRunner == null) {
            return null;
        }

        String coverageRootPath = ContainerPathManager.get().getSystemPath() + File.separator + "coverage";
        String path = coverageRootPath + File.separator + myProject.getName() + coverageFileNameSeparator()
            + FileUtil.sanitizeFileName(myConfiguration.getName()) + ".coverage";

        new File(coverageRootPath).mkdirs();
        return path;
    }

    protected String coverageFileNameSeparator() {
        return "$";
    }
}
