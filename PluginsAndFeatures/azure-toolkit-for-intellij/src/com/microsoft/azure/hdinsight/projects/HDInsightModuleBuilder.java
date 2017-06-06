/**
 * Copyright (c) Microsoft Corporation
 * <p/>
 * All rights reserved.
 * <p/>
 * MIT License
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.microsoft.azure.hdinsight.projects;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.artifacts.JarArtifactType;
import com.intellij.platform.ProjectTemplate;
import com.microsoft.azure.hdinsight.common.CommonConst;
import com.microsoft.azure.hdinsight.projects.ui.HDInsightProjectTypeStep;
import com.microsoft.azuretools.telemetry.AppInsightsClient;
import com.microsoft.intellij.hdinsight.messages.HDInsightBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class HDInsightModuleBuilder extends JavaModuleBuilder implements ModuleBuilderListener {
    private HDInsightProjectTemplate selectedTemplate;
    private HDInsightExternalSystem selectedExternalSystem;
    private List<ProjectTemplate> templates;
    private SparkVersion sparkVersion;
    private ScalaPluginStatus scalaPluginStatus;
    private String sbtVersion;

    private static final String SCALA_PLUGIN_ID = "org.intellij.scala";
    private static final String SCALA_PLUGIN_INSTALL_MSG = "<HTML>No Scala Intellij plugin found. " +
            "Please <FONT color=\"#000099\"><U>CLICK</U></FONT> to install Scala plugin. " +
            "Remember to restart Intellij when installation finished.</HTML>";

    public static final String UniqueKeyName = "UniqueKey";
    public static final String UniqueKeyValue = "HDInsightTool";

    public HDInsightModuleBuilder() {
        this.scalaPluginStatus = ScalaPluginStatus.INSTALLED;
        this.selectedExternalSystem = HDInsightExternalSystem.MAVEN;
        initTemplates();
        this.addListener(this);
    }



    public ScalaPluginStatus getScalaPluginStatus() {
        return scalaPluginStatus;
    }

    @Override
    public String getBuilderId() {
        return "HDInsight";
    }

    @Override
    public Icon getBigIcon() {
        return null;
    }

    @Override
    public Icon getNodeIcon() {
        return IconLoader.getIcon(CommonConst.ProductIConPath);
    }

    @Override
    public String getPresentableName() {
        return "HDInsight";
    }

    @Override
    public String getGroupName() {
        return "HDInsight Tools";
    }

    @Override
    public ModuleType getModuleType() {
        return HDInsightModuleType.getInstance();
    }

    @Override
    public ModuleWizardStep modifySettingsStep(SettingsStep settingsStep) {
        switch (this.selectedTemplate.getTemplateType()) {
            case Scala:
            case ScalaClusterSample:
            case ScalaLocalSample:
                if (null == PluginManager.getPlugin(PluginId.findId(SCALA_PLUGIN_ID))) {
                    if(this.scalaPluginStatus == ScalaPluginStatus.NEED_RESTART) {
                        showRestartMsg();
                    } else {
                        this.scalaPluginStatus = ScalaPluginStatus.NOT_INSTALLED;
                        showScalaPluginInstallMsg();
                    }
                }
                return new SparkScalaSettingsStep(this, settingsStep);
            default:
                return new SparkJavaSettingsStep(this, settingsStep);
        }
    }

    @Nullable
    @Override
    public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
        HDInsightProjectTypeStep step = new HDInsightProjectTypeStep(this);
        Disposer.register(parentDisposable, step);
        return step;
    }

    @Override
    public void moduleCreated(@NotNull Module module) {
        module.setOption(UniqueKeyName, UniqueKeyValue);
        createDefaultArtifact(module);
        switch(this.selectedExternalSystem) {
            case MAVEN:
                new MavenProjectGenerator(module, this.selectedTemplate.getTemplateType(), sparkVersion).generate();
                break;
            case SBT:
                new SbtProjectGenerator(module, this.selectedTemplate.getTemplateType(), sparkVersion, sbtVersion).generate();
                break;
            default:
                new MavenProjectGenerator(module, this.selectedTemplate.getTemplateType(), sparkVersion).generate();
        }

        addTelemetry(this.selectedTemplate.getTemplateType(), sparkVersion);
    }

    public void setSparkVersion(SparkVersion sparkVersion) {
        this.sparkVersion = sparkVersion;
    }

    public void setSelectedTemplate(HDInsightProjectTemplate selectedTemplate) {
        this.selectedTemplate = selectedTemplate;
    }

    public void setSbtVersion(String sbtVersion) {
        this.sbtVersion = sbtVersion;
    }

    public void setSelectedExternalSystem(HDInsightExternalSystem selectedExternalSystem) {
        this.selectedExternalSystem = selectedExternalSystem;
    }

    public List<ProjectTemplate> getTemplates() {
        return this.templates;
    }

    public HDInsightExternalSystem getSelectedExternalSystem() {
        return this.selectedExternalSystem;
    }

    private void showScalaPluginInstallMsg() {
        JLabel label = new JLabel(SCALA_PLUGIN_INSTALL_MSG);
        label.setOpaque(false);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                Set<String> pluginIds = new HashSet<>();
                pluginIds.add(SCALA_PLUGIN_ID);
                PluginsAdvertiser.installAndEnablePlugins(pluginIds, new Runnable() {
                    @Override
                    public void run() {
                        scalaPluginStatus = ScalaPluginStatus.NEED_RESTART;
                    }
                });
            }
        });
        JOptionPane.showMessageDialog(null, label, "Scala Plugin Check", JOptionPane.ERROR_MESSAGE);
    }

    private void initTemplates() {
        this.templates = new ArrayList<>();
        this.templates.add(new HDInsightProjectTemplate(HDInsightTemplatesType.Java));
        this.templates.add(new HDInsightProjectTemplate(HDInsightTemplatesType.JavaLocalSample));
        this.templates.add(new HDInsightProjectTemplate(HDInsightTemplatesType.Scala));
        this.templates.add(new HDInsightProjectTemplate(HDInsightTemplatesType.ScalaLocalSample));
        this.templates.add(new HDInsightProjectTemplate(HDInsightTemplatesType.ScalaClusterSample));
    }

    private void showRestartMsg() {
        if (PluginManagerConfigurable.showRestartDialog() == Messages.YES) {
            ApplicationManagerEx.getApplicationEx().restart(true);
        }
    }

    private void addTelemetry(HDInsightTemplatesType templatesType, SparkVersion sparkVersion) {
        Map<String, String> hdiProperties = new HashMap<String, String>();
        hdiProperties.put("Spark Version", sparkVersion.toString());

        if (templatesType == HDInsightTemplatesType.Java) {
            AppInsightsClient.create(HDInsightBundle.message("SparkProjectSystemJavaCreation"), null, hdiProperties);
        } else if (templatesType == HDInsightTemplatesType.JavaLocalSample) {
            AppInsightsClient.create(HDInsightBundle.message("SparkProjectSystemJavaSampleCreation"), null, hdiProperties);
        } else if (templatesType == HDInsightTemplatesType.Scala) {
            AppInsightsClient.create(HDInsightBundle.message("SparkProjectSystemScalaCreation"), null, hdiProperties);
        } else if (templatesType == HDInsightTemplatesType.ScalaClusterSample || templatesType == HDInsightTemplatesType.ScalaLocalSample) {
            AppInsightsClient.create(HDInsightBundle.message("SparkProjectSystemScalaSampleCreation"), null, hdiProperties);
        } else {
            AppInsightsClient.create(HDInsightBundle.message("SparkProjectSystemOtherCreation"), null, hdiProperties);
        }
    }

    private void createDefaultArtifact(final Module module) {
        final Project project = module.getProject();
        final JarArtifactType type = new JarArtifactType();
        final PackagingElementFactory factory = PackagingElementFactory.getInstance();
        CompositePackagingElement root = factory.createArchive("default_artifact.jar");
        root.addOrFindChild(factory.createModuleOutput(module));
        ArtifactManager.getInstance(project).addArtifact(module.getName() + "_DefaultArtifact", type, root);
    }
}
