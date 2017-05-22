package net.belehradek.fuml.gradle;
 
import org.gradle.api.*;
import org.gradle.api.tasks.JavaExec;

import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

// import org.modeldriven.alf.eclipse.papyrus.execution.Fuml;
// import org.modeldriven.alf.fuml.impl.execution.Alf;
// import org.modeldriven.alf.eclipse.fuml.execution.AlfCompiler;

//TODO: alf knihovny nekde externe na disku, spoustet pres exec

class FumlPluginExtension {
    String libPath;
    String unitName;
    String toolsPath;
    String transFile;
    String namespacePrefix;
}
 
class FumlPlugin implements Plugin<Project> {

    protected String FumlPluginExtensionName = "fUmlSettings";

    protected Project project;

    //-------------------------------------------------------------------------

    public String getLibPath() {
        if (project.fUmlSettings.libPath != null)
            return project.fUmlSettings.libPath;
        return "fUmlLibraries";
    }

    public String getToolsPath() {
        if (project.fUmlSettings.toolsPath != null)
            return project.fUmlSettings.toolsPath;
        return "fUmlTools";
    }

    public String getSourceFumlPath() {
        //return ''+project.rootDir+'\\src\\main\\fuml';
        return "src\\main\\fuml";
    }

    public String getSourceAlfPath() {
        //return ''+project.rootDir+'\\src\\main\\alf';
        return "src\\main\\alf";
    }

    public String getBuildPath() {
        //return 'file:///' + project.rootDir.toString().replace('\\', '/') + '/build/fuml';
        return "uml";
    }

    public String getOutPath() {
        return "out";
    }

    public String getNamespacePrefix() {
        if (project.fUmlSettings.namespacePrefix != null)
            return project.fUmlSettings.namespacePrefix;
        return "generated";
    }

    public String getGenerationTemplate() {
        String n = "root.ftl";
        if (project.fUmlSettings.transFile != null)
            n = project.fUmlSettings.transFile;
        return 'src\\main\\ftl\\' + n;
    }

    public String getUnitName() {
        if (project.fUmlSettings.unitName != null)
            return project.fUmlSettings.unitName;
        return "App";
    }

    //-------------------------------------------------------------------------

    //spusti main metody z dane tridy s parametry
    public void executeClassMain(String name, String[] params) {
        println 'Execute ' + name + '.Main(' + params + ')';
        Class<?> c = this.class.classLoader.loadClass(name);
        c."main"(params);
    }

    //spusti jar soubor s parametry
    public void executeJar(String path, String[] params) {
        def all = [path, *params];
        println 'Execute jar ' + all;
        project.javaexec {
            main="-jar";
            args = all;
        } 
    }

    //rozbaluje slozku ze zipu(jaru)
    public void unzipFolderFromResources(String resFolder, String outFolder, Boolean content) {
        ZipFile zipFile = new ZipFile(FumlPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        Enumeration<?> zfe = zipFile.entries();
        while (zfe.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) zfe.nextElement();

            String name = zipEntry.getName();

            if (!name.startsWith(resFolder)) continue;
                
            long size = zipEntry.getSize();
            long compressedSize = zipEntry.getCompressedSize();
            System.out.printf("name: %-20s | size: %6d | compressed size: %6d\n", name, size, compressedSize);

            //pouze obsah slozky, ne slozku samotnou
            if (content && name.startsWith(resFolder+"/")) {
                name = name.substring(resFolder.length() + 1);
                if (name == "" || name == "/")
                    continue;
            }

            // Do we need to create a directory ?
            File file = new File(outFolder + "/" + name);
            if (name.endsWith("/")) {
                file.mkdirs();
                continue;
            }

            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            // Extract the file
            InputStream is = zipFile.getInputStream(zipEntry);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = is.read(bytes)) >= 0) {
                fos.write(bytes, 0, length);
            }
            is.close();
            fos.close();

        }
        zipFile.close();
    }

    //-------------------------------------------------------------------------

    void apply(Project project) {

        this.project = project;

        project.extensions.create(FumlPluginExtensionName, FumlPluginExtension);

        project.task('fUmlInfo').doLast {
            println "Hello from fUml plugin: "
            println " -rootProject:   " + project.rootProject;
            println " -rootDir:       " + project.rootDir;
            println " -projectDir:    " + project.projectDir;
            println " -libPath:       " + getLibPath();
            println " -sourceAlfPath: " + getSourceAlfPath();
            println " -sourceFumlPath:" + getSourceFumlPath();
            println " -buildPath:     " + getBuildPath();
            println " -unitName:      " + getUnitName();
        }
        
        //instalace pluginu - rozbaleni fuml knihovny a nastroju do slozky
        project.task('fUmlInstall').doLast {
            String targetFolder = project.projectDir;
            unzipFolderFromResources("fUmlLibraries", targetFolder, false);
            unzipFolderFromResources("fUmlTools", targetFolder, false);
        }

        //instalace projektu - vytvoreni slozek
        project.task('fUmlInit').doLast {
            String targetFolder = project.projectDir;
            unzipFolderFromResources("fUmlFiles", targetFolder, true);
        }

        //cisteni generovanych souboru
        project.task('fUmlClean').doLast {
            project.delete { delete 'build' }
            project.delete { delete 'out' }
            project.delete { delete 'uml' }
        }

        //---------------------------------------------------------------------

        //generovani kodu z ALFu
        project.task('fUmlCodeGenerate').doLast {
            String[] s = [
                '-l', getLibPath(), 
                '-m', getSourceAlfPath(),
                '-u', getBuildPath(), 
                '-o', getOutPath(), 
                '-p', getNamespacePrefix(), 
                '-t', getGenerationTemplate(), 
                '-n', getUnitName()
            ] as String[];
            //executeClassMain('org.modeldriven.alf.eclipse.papyrus.execution.Fuml', s);
            executeJar(getToolsPath() + "/fUmlCodeGenerator.jar", s);
        }

        //kompiluje alf jednotku ze src do build
        project.task('fUmlCompile').doLast {
            String[] s = [
                '-l', getLibPath(), 
                '-m', getSourceAlfPath(), 
                '-u', getBuildPath(), 
                getUnitName()
            ] as String[];
            //executeClassMain('org.modeldriven.alf.eclipse.fuml.execution.AlfCompiler', s);
            executeJar(getToolsPath() + "/alf-eclipse.jar", s);
        }

        //spousti vygenerovane fUml
        project.task('fUmlRun').doLast {
            String[] s = [
                '-l', getLibPath(), 
                '-u', getBuildPath(), 
                getUnitName()
            ] as String[];
            //executeClassMain('org.modeldriven.alf.eclipse.papyrus.execution.Fuml', s);
            executeJar(getToolsPath() + "/fuml-eclipse.jar", s);
        }

        //spousti alf bez generovani
        project.task('fUmlRunAlf').doLast {
            String[] s = [
                '-l', getLibPath(), 
                '-m', getSourceAlfPath(), 
                getUnitName()
            ] as String[];
            //executeClassMain('org.modeldriven.alf.eclipse.papyrus.execution.Fuml', s);
            executeJar(getToolsPath() + "/alf.jar", s);
        }
    }
}