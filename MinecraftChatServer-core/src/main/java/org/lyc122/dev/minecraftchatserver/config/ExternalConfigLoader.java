package org.lyc122.dev.minecraftchatserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 外部配置加载器
 * 在应用启动时，将内置配置文件释放到外部目录，并加载外部配置
 */
@Slf4j
/**
 * 注意：此类使用传统的 EnvironmentPostProcessor 机制
 * 在 Spring Boot 3.x+ 中，推荐使用 BootstrapRegistryInitializer 或 ConfigDataLoader
 * 但 EnvironmentPostProcessor 仍然兼容并有效
 */
public class ExternalConfigLoader implements EnvironmentPostProcessor {

    private static final String CONFIG_DIR_NAME = "config";
    private static final String MAIN_CONFIG_FILE = "application.properties";
    private static final String DEV_CONFIG_FILE = "application-dev.properties";
    private static final String FIRST_RUN_FLAG = "chatserver.first.run";
    private static final String INIT_MARKER_FILE = ".initialized";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("========== 外部配置加载器启动 ==========");
        try {
            // 获取外部配置目录路径
            Path externalConfigDir = getExternalConfigDir();
            
            // 确保配置目录存在
            ensureConfigDirExists(externalConfigDir);
            
            // 检查是否是首次运行（配置文件不存在）
            boolean isFirstRun = !Files.exists(externalConfigDir.resolve(MAIN_CONFIG_FILE));
            
            // 释放默认配置文件（如果不存在）
            extractDefaultConfigIfNeeded(externalConfigDir, MAIN_CONFIG_FILE);
            extractDefaultConfigIfNeeded(externalConfigDir, DEV_CONFIG_FILE);
            
            // 如果是首次运行，设置标志并显示配置提示
            if (isFirstRun) {
                System.setProperty(FIRST_RUN_FLAG, "true");
                printFirstRunMessage(externalConfigDir);
                // 阻止应用继续启动
                throw new FirstRunException("首次启动：配置文件已释放，请配置后重新启动服务器");
            }
            
            // 检查是否需要初始化数据库（没有初始化标记文件）
            boolean needDbInit = !Files.exists(externalConfigDir.resolve(INIT_MARKER_FILE));
            
            // 加载外部主配置文件
            loadExternalConfig(environment, externalConfigDir, MAIN_CONFIG_FILE);
            
            // 根据是否需要初始化，动态设置 ddl-auto
            if (needDbInit) {
                log.info("检测到首次运行，将使用 'create' 模式初始化数据库");
                setDdlAuto(environment, "create");
                // 创建初始化标记文件
                createInitMarker(externalConfigDir);
            } else {
                log.info("数据库已初始化，将使用 'validate' 模式");
                setDdlAuto(environment, "validate");
            }
            
            // 加载外部 profile-specific 配置文件（如果存在 active profile）
            String[] activeProfiles = environment.getActiveProfiles();
            if (activeProfiles.length > 0) {
                for (String profile : activeProfiles) {
                    String profileConfigFile = "application-" + profile + ".properties";
                    loadExternalConfig(environment, externalConfigDir, profileConfigFile);
                }
            }
            
            log.info("========== 外部配置加载完成，配置目录: {} ==========", externalConfigDir.toAbsolutePath());
            
        } catch (FirstRunException e) {
            // 重新抛出以阻止应用启动
            throw e;
        } catch (Exception e) {
            log.error("外部配置加载失败，将使用内置配置", e);
        }
    }

    /**
     * 动态设置 Hibernate ddl-auto
     */
    private void setDdlAuto(ConfigurableEnvironment environment, String ddlAuto) {
        Properties props = new Properties();
        props.setProperty("spring.jpa.hibernate.ddl-auto", ddlAuto);
        PropertiesPropertySource propertySource = new PropertiesPropertySource(
            "dynamic-ddl-auto", props);
        // 添加到最前面，覆盖其他配置
        environment.getPropertySources().addFirst(propertySource);
        log.info("已设置 Hibernate ddl-auto 为: {}", ddlAuto);
    }

    /**
     * 创建初始化标记文件
     */
    private void createInitMarker(Path configDir) {
        try {
            Path markerFile = configDir.resolve(INIT_MARKER_FILE);
            Files.writeString(markerFile, "Database initialized at: " + System.currentTimeMillis());
            log.info("已创建数据库初始化标记文件: {}", markerFile);
        } catch (IOException e) {
            log.error("创建初始化标记文件失败", e);
        }
    }

    /**
     * 首次运行提示信息
     */
    private void printFirstRunMessage(Path configDir) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  首次启动检测到！配置文件已释放到外部目录");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.println("  配置目录: " + configDir.toAbsolutePath());
        System.out.println();
        System.out.println("  已释放的文件:");
        System.out.println("    - application.properties      (主配置文件 - MySQL)");
        System.out.println("    - application-dev.properties  (开发配置 - H2内存数据库)");
        System.out.println();
        System.out.println("  请根据您的需求修改配置文件:");
        System.out.println();
        System.out.println("  1. 如果使用 MySQL (生产环境):");
        System.out.println("     - 编辑 application.properties");
        System.out.println("     - 设置数据库连接: spring.datasource.url");
        System.out.println("     - 设置用户名密码: spring.datasource.username/password");
        System.out.println();
        System.out.println("  2. 如果使用 H2 (开发测试):");
        System.out.println("     - 使用命令: java -jar -Dspring.profiles.active=dev <jar文件>");
        System.out.println();
        System.out.println("  配置完成后，重新启动服务器。");
        System.out.println("=".repeat(70) + "\n");
    }

    /**
     * 首次运行异常 - 用于阻止应用继续启动
     */
    public static class FirstRunException extends RuntimeException {
        public FirstRunException(String message) {
            super(message);
        }
    }

    /**
     * 获取外部配置目录路径
     * 优先使用系统属性 chatserver.config.dir，否则使用运行目录下的 config 文件夹
     */
    private Path getExternalConfigDir() {
        String customConfigDir = System.getProperty("chatserver.config.dir");
        if (customConfigDir != null && !customConfigDir.isBlank()) {
            return Paths.get(customConfigDir);
        }
        return Paths.get(System.getProperty("user.dir"), CONFIG_DIR_NAME);
    }

    /**
     * 确保配置目录存在
     */
    private void ensureConfigDirExists(Path configDir) throws IOException {
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
            log.info("创建配置目录: {}", configDir.toAbsolutePath());
        }
    }

    /**
     * 如果外部配置文件不存在，则从资源目录释放默认配置
     */
    private void extractDefaultConfigIfNeeded(Path configDir, String configFileName) {
        Path externalFile = configDir.resolve(configFileName);
        
        if (Files.exists(externalFile)) {
            log.debug("外部配置文件已存在，跳过释放: {}", externalFile);
            return;
        }

        try {
            // 从 classpath 读取默认配置
            String defaultConfig = readResourceConfig(configFileName);
            if (defaultConfig == null) {
                log.warn("未找到内置配置文件: {}", configFileName);
                return;
            }

            // 写入外部文件
            Files.writeString(externalFile, defaultConfig, StandardCharsets.UTF_8);
            log.info("已释放默认配置文件: {}", externalFile.toAbsolutePath());
            
        } catch (IOException e) {
            log.error("释放配置文件失败: {}", configFileName, e);
        }
    }

    /**
     * 从资源目录读取配置文件内容
     */
    private String readResourceConfig(String configFileName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(configFileName)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 加载外部配置文件到 Spring Environment
     */
    private void loadExternalConfig(ConfigurableEnvironment environment, Path configDir, String configFileName) {
        Path externalFile = configDir.resolve(configFileName);
        
        if (!Files.exists(externalFile)) {
            return;
        }

        try {
            Resource resource = new FileSystemResource(externalFile.toFile());
            Properties props = PropertiesLoaderUtils.loadProperties(resource);
            
            if (!props.isEmpty()) {
                PropertiesPropertySource propertySource = new PropertiesPropertySource(
                    "external-" + configFileName, props);
                // 添加到配置源的最前面，优先级最高
                environment.getPropertySources().addFirst(propertySource);
                log.info("已加载外部配置: {} ({} 个属性)", externalFile, props.size());
            }
            
        } catch (IOException e) {
            log.error("加载外部配置失败: {}", externalFile, e);
        }
    }
}
