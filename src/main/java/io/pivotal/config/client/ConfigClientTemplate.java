/**
 *
 */
package io.pivotal.config.client;

import org.apache.commons.logging.Log;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.context.config.RandomValuePropertySource;
import org.springframework.boot.env.EnumerableCompositePropertySource;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

import java.io.IOException;
import java.util.*;

/**
 * @author malston
 */
public class ConfigClientTemplate<T> implements io.pivotal.config.client.PropertySourceProvider {

    private static final String HTTPS_SCHEME = "https://";

    private static final String HTTP_SCHEME = "http://";

    private final ConfigurableEnvironment environment = new StandardEnvironment();

    private final ConfigClientProperties defaults;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ConfigFileEnvironmentProcessor configFileEnvironmentProcessor;

    public ConfigClientTemplate(final String configServerUrl, final String app, final String[] profiles) {
        Assert.hasLength(configServerUrl, "You MUST set the config server URI");
        if (!configServerUrl.startsWith(HTTP_SCHEME) && !configServerUrl.startsWith(HTTPS_SCHEME)) {
            throw new RuntimeException("You MUST put the URI scheme in front of the config server URI");
        }
        Map<String, Object> defaultProperties = new HashMap<>();
        defaultProperties.put("spring.application.name", app);
        this.environment.getPropertySources()
                .addLast(new MapPropertySource(ConfigFileEnvironmentProcessor.DEFAULT_PROPERTIES, defaultProperties));
        for (String profile : profiles) {
            this.environment.addActiveProfile(profile);
        }
        this.defaults = new ConfigClientProperties(environment);
        this.defaults.setFailFast(false);
        this.defaults.setUri(configServerUrl);
        DefaultUriTemplateHandler uriTemplateHandler = new DefaultUriTemplateHandler();
        uriTemplateHandler.setBaseUrl(configServerUrl);
        this.restTemplate.setUriTemplateHandler(uriTemplateHandler);
        ConfigServicePropertySourceLocator locator = new ConfigServicePropertySourceLocator(defaults);
        this.configFileEnvironmentProcessor = new ConfigFileEnvironmentProcessor(environment, locator);
    }

    @SuppressWarnings("unchecked")
    public T getProperty(String name) {
        return (T) configFileEnvironmentProcessor.getPropertySource().getProperty(name);
    }

    public PropertySource<?> getPropertySource() {
        return configFileEnvironmentProcessor.getPropertySource();
    }

    static final class ConfigFileEnvironmentProcessor extends ConfigFileApplicationListener
            implements PropertySourceProvider {

        private static final String DEFAULT_PROPERTIES = "defaultProperties";

        // Note the order is from least to most specific (last one wins)
        private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/";

        private static final String DEFAULT_NAMES = "application";

        /**
         * Name of the application configuration {@link PropertySource}.
         */
        public static final String APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME = ConfigFileApplicationListener.APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME;

        private final DeferredLog logger = new DeferredLog();

        private String searchLocations;

        private String names;

        private final ResourceLoader resourceLoader;

        private final ConfigurableEnvironment environment;

        private final ConfigServicePropertySourceLocator locator;

        private PropertySource<?> source;

        public ConfigFileEnvironmentProcessor(ConfigurableEnvironment environment, ConfigServicePropertySourceLocator locator) {
            this.environment = environment;
            this.locator = locator;
            this.resourceLoader = new DefaultResourceLoader();
        }

        public PropertySource<?> getPropertySource() {
            if (source != null) {
                return source;
            }
            source = getPropertySource(this.locator);

            return source == null ? this.environment.getPropertySources().get(APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME) : source;
        }

        public void refresh() {
            source = getPropertySource(this.locator);
        }


        public ResourceLoader getResourceLoader() {
            return this.resourceLoader;
        }

        private PropertySource<?> getPropertySource(ConfigServicePropertySourceLocator locator) {
            PropertySource<?> source = locator.locate(this.environment);
            if (source != null) {
                this.environment.getPropertySources().addFirst(source);
            }

            addPropertySources(environment, this.getResourceLoader());
            addPropertySources((CompositePropertySource) source);

            return source;
        }

        private void addPropertySources(CompositePropertySource composite) {
//            System.out.println("Adding composite property sources: " + composite.getName());
            if (environment != null && composite != null) {
                if (environment.getPropertySources() != null) {
                    for (PropertySource source : environment.getPropertySources()) {
//                        System.out.println("Env prop source: " + source.getName());
                        if (source.getSource() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) source.getSource();
                            composite.addPropertySource(new MapPropertySource(source.getName(), map));
                        } else if (source.getSource() instanceof List) {
                            List sources = (List) source.getSource();
                            for (Object src : sources) {
                                if (src instanceof EnumerablePropertySource) {
                                    EnumerablePropertySource enumerable = (EnumerablePropertySource) src;
//                                    System.out.println("Adding enumerable property source: " + enumerable.getName());
//                                    System.out.println("Property names: " + enumerable.getPropertyNames()[0]);
                                    composite.addPropertySource(enumerable);
                                }
                            }
                        } else if (!(source instanceof CompositePropertySource)) {
                            composite.addPropertySource(source);
                        }
                    }
                }
            }
        }

        /**
         * Add config file property sources to the specified environment.
         * @param environment the environment to add source to
         * @param resourceLoader the resource loader
         * @see #addPostProcessors(ConfigurableApplicationContext)
         */
        protected void addPropertySources(ConfigurableEnvironment environment,
                                          ResourceLoader resourceLoader) {
            RandomValuePropertySource.addToEnvironment(environment);
            try {
                new Loader(environment, resourceLoader).load();
            }
            catch (IOException ex) {
                throw new IllegalStateException("Unable to load configuration files", ex);
            }
        }

        /**
         * Set the search locations that will be considered as a comma-separated list. Each
         * search location should be a directory path (ending in "/") and it will be prefixed
         * by the file names constructed from {@link #setSearchNames(String) search names} and
         * profiles (if any) plus file extensions supported by the properties loaders.
         * Locations are considered in the order specified, with later items taking precedence
         * (like a map merge).
         * @param locations the search locations
         */
        public void setSearchLocations(String locations) {
            Assert.hasLength(locations, "Locations must not be empty");
            this.searchLocations = locations;
        }

        /**
         * Sets the names of the files that should be loaded (excluding file extension) as a
         * comma-separated list.
         * @param names the names to load
         */
        public void setSearchNames(String names) {
            Assert.hasLength(names, "Names must not be empty");
            this.names = names;
        }

        /**
         * Loads candidate property sources and configures the active profiles.
         */
        private class Loader {

            private final Log logger = ConfigFileEnvironmentProcessor.this.logger;

            private final ConfigurableEnvironment environment;

            private final ResourceLoader resourceLoader;

            private io.pivotal.config.client.PropertySourcesLoader propertiesLoader;

            private Queue<Profile> profiles;

            private List<Profile> processedProfiles;

            private boolean activatedProfiles;

            Loader(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
                this.environment = environment;
                this.resourceLoader = resourceLoader == null ? new DefaultResourceLoader()
                        : resourceLoader;
            }

            public void load() throws IOException {
                this.propertiesLoader = new io.pivotal.config.client.PropertySourcesLoader();
                this.activatedProfiles = false;
                this.profiles = Collections.asLifoQueue(new LinkedList<Profile>());
                this.processedProfiles = new LinkedList<Profile>();

                // Pre-existing active profiles set via Environment.setActiveProfiles()
                // are additional profiles and config files are allowed to add more if
                // they want to, so don't call addActiveProfiles() here.
                Set<Profile> initialActiveProfiles = initializeActiveProfiles();
                this.profiles.addAll(getUnprocessedActiveProfiles(initialActiveProfiles));
                if (this.profiles.isEmpty()) {
                    for (String defaultProfileName : this.environment.getDefaultProfiles()) {
                        Profile defaultProfile = new Profile(defaultProfileName, true);
                        if (!this.profiles.contains(defaultProfile)) {
                            this.profiles.add(defaultProfile);
                        }
                    }
                }

                // The default profile for these purposes is represented as null. We add it
                // last so that it is first out of the queue (active profiles will then
                // override any settings in the defaults when the list is reversed later).
                this.profiles.add(null);

                while (!this.profiles.isEmpty()) {
                    Profile profile = this.profiles.poll();
                    for (String location : getSearchLocations()) {
                        if (!location.endsWith("/")) {
                            // location is a filename already, so don't search for more
                            // filenames
                            load(location, null, profile);
                        }
                        else {
                            for (String name : getSearchNames()) {
                                load(location, name, profile);
                            }
                        }
                    }
                    this.processedProfiles.add(profile);
                }

                addConfigurationProperties(this.propertiesLoader.getPropertySources());
            }

            private Set<Profile> initializeActiveProfiles() {
                if (!this.environment.containsProperty(ACTIVE_PROFILES_PROPERTY)) {
                    return Collections.emptySet();
                }
                // Any pre-existing active profiles set via property sources (e.g. System
                // properties) take precedence over those added in config files.
                Set<Profile> activeProfiles = getProfilesForValue(
                        this.environment.getProperty(ACTIVE_PROFILES_PROPERTY));
                maybeActivateProfiles(activeProfiles);
                return activeProfiles;
            }

            /**
             * Return the active profiles that have not been processed yet. If a profile is
             * enabled via both {@link #ACTIVE_PROFILES_PROPERTY} and
             * {@link ConfigurableEnvironment#addActiveProfile(String)} it needs to be
             * filtered so that the {@link #ACTIVE_PROFILES_PROPERTY} value takes precedence.
             * <p>
             * Concretely, if the "cloud" profile is enabled via the environment, it will take
             * less precedence that any profile set via the {@link #ACTIVE_PROFILES_PROPERTY}.
             * @param initialActiveProfiles the profiles that have been enabled via
             * {@link #ACTIVE_PROFILES_PROPERTY}
             * @return the unprocessed active profiles from the environment to enable
             */
            private List<Profile> getUnprocessedActiveProfiles(
                    Set<Profile> initialActiveProfiles) {
                List<Profile> unprocessedActiveProfiles = new ArrayList<Profile>();
                for (String profileName : this.environment.getActiveProfiles()) {
                    Profile profile = new Profile(profileName);
                    if (!initialActiveProfiles.contains(profile)) {
                        unprocessedActiveProfiles.add(profile);
                    }
                }
                // Reverse them so the order is the same as from getProfilesForValue()
                // (last one wins when properties are eventually resolved)
                Collections.reverse(unprocessedActiveProfiles);
                return unprocessedActiveProfiles;
            }

            private void load(String location, String name, Profile profile)
                    throws IOException {
//                System.out.println("loading " + location + " name: " + name + " profile: " + profile);
                String group = "profile=" + (profile == null ? "" : profile);
                if (!StringUtils.hasText(name)) {
                    // Try to load directly from the location
                    loadIntoGroup(group, location, profile);
                }
                else {
                    // Search for a file with the given name
                    for (String ext : this.propertiesLoader.getAllFileExtensions()) {
//                        System.out.println("Extension: " + ext);
                        if (profile != null) {
                            // Try the profile-specific file
                            loadIntoGroup(group, location + name + "-" + profile + "." + ext,
                                    null);
                            for (Profile processedProfile : this.processedProfiles) {
                                if (processedProfile != null) {
                                    loadIntoGroup(group, location + name + "-"
                                            + processedProfile + "." + ext, profile);
                                }
                            }
                            // Sometimes people put "spring.profiles: dev" in
                            // application-dev.yml (gh-340). Arguably we should try and error
                            // out on that, but we can be kind and load it anyway.
                            loadIntoGroup(group, location + name + "-" + profile + "." + ext,
                                    profile);
                        }
                        // Also try the profile-specific section (if any) of the normal file
                        loadIntoGroup(group, location + name + "." + ext, profile);
                    }
                }
            }

            private PropertySource<?> loadIntoGroup(String identifier, String location,
                                                    Profile profile) throws IOException {
//                System.out.println("Loading into group: " + identifier);
                Resource resource = this.resourceLoader.getResource(location);
//                System.out.println("Resource is: " + resource.getFilename() + " with location: " + location);
                PropertySource<?> propertySource = null;
                StringBuilder msg = new StringBuilder();
                if (resource != null && resource.exists()) {
                    String name = "applicationConfig: [" + location + "]";
                    String group = "applicationConfig: [" + identifier + "]";
                    propertySource = this.propertiesLoader.load(resource, group, name,
                            (profile == null ? null : profile.getName()));
                    if (propertySource != null) {
//                        System.out.println("Found property source: " + propertySource.getName());
                        msg.append("Loaded ");
                        handleProfileProperties(propertySource);
                    }
                    else {
                        msg.append("Skipped (empty) ");
                    }
                }
                else {
                    msg.append("Skipped ");
                }
                msg.append("config file ");
                msg.append(getResourceDescription(location, resource));
                if (profile != null) {
                    msg.append(" for profile ").append(profile);
                }
                if (resource == null || !resource.exists()) {
                    msg.append(" resource not found");
                    this.logger.trace(msg);
                }
                else {
                    this.logger.debug(msg);
                }
                return propertySource;
            }

            private String getResourceDescription(String location, Resource resource) {
                String resourceDescription = "'" + location + "'";
                if (resource != null) {
                    try {
                        resourceDescription = String.format("'%s' (%s)",
                                resource.getURI().toASCIIString(), location);
                    }
                    catch (IOException ex) {
                        // Use the location as the description
                    }
                }
                return resourceDescription;
            }

            private void handleProfileProperties(PropertySource<?> propertySource) {
                Set<Profile> activeProfiles = getProfilesForValue(
                        propertySource.getProperty(ACTIVE_PROFILES_PROPERTY));
                maybeActivateProfiles(activeProfiles);
                Set<Profile> includeProfiles = getProfilesForValue(
                        propertySource.getProperty(INCLUDE_PROFILES_PROPERTY));
                addProfiles(includeProfiles);
            }

            private void maybeActivateProfiles(Set<Profile> profiles) {
                if (this.activatedProfiles) {
                    if (!profiles.isEmpty()) {
                        this.logger.debug("Profiles already activated, '" + profiles
                                + "' will not be applied");
                    }
                    return;
                }
                if (!profiles.isEmpty()) {
                    addProfiles(profiles);
                    this.logger.debug("Activated profiles "
                            + StringUtils.collectionToCommaDelimitedString(profiles));
                    this.activatedProfiles = true;
                    removeUnprocessedDefaultProfiles();
                }
            }

            private void removeUnprocessedDefaultProfiles() {
                for (Iterator<Profile> iterator = this.profiles.iterator(); iterator
                        .hasNext();) {
                    if (iterator.next().isDefaultProfile()) {
                        iterator.remove();
                    }
                }
            }

            private Set<Profile> getProfilesForValue(Object property) {
                String value = (property == null ? null : property.toString());
                Set<String> profileNames = asResolvedSet(value, null);
                Set<Profile> profiles = new LinkedHashSet<Profile>();
                for (String profileName : profileNames) {
                    profiles.add(new Profile(profileName));
                }
                return profiles;
            }

            private void addProfiles(Set<Profile> profiles) {
                for (Profile profile : profiles) {
                    this.profiles.add(profile);
                    if (!environmentHasActiveProfile(profile.getName())) {
                        // If it's already accepted we assume the order was set
                        // intentionally
                        prependProfile(this.environment, profile);
                    }
                }
            }

            private boolean environmentHasActiveProfile(String profile) {
                for (String activeProfile : this.environment.getActiveProfiles()) {
                    if (activeProfile.equals(profile)) {
                        return true;
                    }
                }
                return false;
            }

            private void prependProfile(ConfigurableEnvironment environment,
                                        Profile profile) {
                Set<String> profiles = new LinkedHashSet<String>();
                environment.getActiveProfiles(); // ensure they are initialized
                // But this one should go first (last wins in a property key clash)
                profiles.add(profile.getName());
                profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
                environment.setActiveProfiles(profiles.toArray(new String[profiles.size()]));
            }

            private Set<String> getSearchLocations() {
                Set<String> locations = new LinkedHashSet<String>();
                // User-configured settings take precedence, so we do them first
                if (this.environment.containsProperty(CONFIG_LOCATION_PROPERTY)) {
                    for (String path : asResolvedSet(
                            this.environment.getProperty(CONFIG_LOCATION_PROPERTY), null)) {
                        if (!path.contains("$")) {
                            path = StringUtils.cleanPath(path);
                            if (!ResourceUtils.isUrl(path)) {
                                path = ResourceUtils.FILE_URL_PREFIX + path;
                            }
                        }
                        locations.add(path);
                    }
                }
                locations.addAll(
                        asResolvedSet(ConfigFileEnvironmentProcessor.this.searchLocations,
                                DEFAULT_SEARCH_LOCATIONS));
                return locations;
            }

            private Set<String> getSearchNames() {
                if (this.environment.containsProperty(CONFIG_NAME_PROPERTY)) {
                    return asResolvedSet(this.environment.getProperty(CONFIG_NAME_PROPERTY),
                            null);
                }
                return asResolvedSet(ConfigFileEnvironmentProcessor.this.names, DEFAULT_NAMES);
            }

            private Set<String> asResolvedSet(String value, String fallback) {
                List<String> list = Arrays.asList(StringUtils.trimArrayElements(
                        StringUtils.commaDelimitedListToStringArray(value != null
                                ? this.environment.resolvePlaceholders(value) : fallback)));
                Collections.reverse(list);
                return new LinkedHashSet<String>(list);
            }

            private void addConfigurationProperties(MutablePropertySources sources) {
//                System.out.println("Adding configuration properties from property loader");
                List<PropertySource<?>> reorderedSources = new ArrayList<PropertySource<?>>();
                for (PropertySource<?> item : sources) {
                    reorderedSources.add(item);
                }
                addConfigurationProperties(
                        new ConfigurationPropertySources(reorderedSources));
            }

            private void addConfigurationProperties(
                    ConfigurationPropertySources configurationSources) {
                MutablePropertySources existingSources = this.environment
                        .getPropertySources();
                if (existingSources.contains(DEFAULT_PROPERTIES)) {
                    existingSources.addBefore(DEFAULT_PROPERTIES, configurationSources);
                }
                else {
                    existingSources.addLast(configurationSources);
                }
            }

        }

        private static class Profile {

            private final String name;

            private final boolean defaultProfile;

            Profile(String name) {
                this(name, false);
            }

            Profile(String name, boolean defaultProfile) {
                Assert.notNull(name, "Name must not be null");
                this.name = name;
                this.defaultProfile = defaultProfile;
            }

            public String getName() {
                return this.name;
            }

            public boolean isDefaultProfile() {
                return this.defaultProfile;
            }

            @Override
            public String toString() {
                return this.name;
            }

            @Override
            public int hashCode() {
                return this.name.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != getClass()) {
                    return false;
                }
                return ((Profile) obj).name.equals(this.name);
            }

        }

        /**
         * Holds the configuration {@link PropertySource}s as they are loaded can relocate
         * them once configuration classes have been processed.
         */
        static class ConfigurationPropertySources
                extends EnumerablePropertySource<Collection<PropertySource<?>>> {

            private final Collection<PropertySource<?>> sources;

            private final String[] names;

            ConfigurationPropertySources(Collection<PropertySource<?>> sources) {
                super(APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME, sources);
                this.sources = sources;
                List<String> names = new ArrayList<String>();
                for (PropertySource<?> source : sources) {
                    if (source instanceof EnumerablePropertySource) {
                        names.addAll(Arrays.asList(
                                ((EnumerablePropertySource<?>) source).getPropertyNames()));
                    }
                }
                this.names = names.toArray(new String[names.size()]);
            }

            @Override
            public Object getProperty(String name) {
                for (PropertySource<?> propertySource : this.sources) {
                    Object value = propertySource.getProperty(name);
                    if (value != null) {
                        return value;
                    }
                }
                return null;
            }

            public static void finishAndRelocate(MutablePropertySources propertySources) {
                String name = APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME;
                ConfigurationPropertySources removed = (ConfigurationPropertySources) propertySources
                        .get(name);
                if (removed != null) {
                    for (PropertySource<?> propertySource : removed.sources) {
                        if (propertySource instanceof EnumerableCompositePropertySource) {
                            EnumerableCompositePropertySource composite = (EnumerableCompositePropertySource) propertySource;
                            for (PropertySource<?> nested : composite.getSource()) {
                                propertySources.addAfter(name, nested);
                                name = nested.getName();
                            }
                        }
                        else {
                            propertySources.addAfter(name, propertySource);
                        }
                    }
                    propertySources.remove(APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME);
                }
            }

            @Override
            public String[] getPropertyNames() {
                return this.names;
            }

        }
    }

}
