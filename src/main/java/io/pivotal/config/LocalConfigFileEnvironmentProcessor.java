package io.pivotal.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.bind.PropertySourcesPropertyValues;
import org.springframework.boot.bind.RelaxedDataBinder;
import org.springframework.boot.context.config.RandomValuePropertySource;
import org.springframework.boot.env.PropertySourcesLoader;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * TODO: Make this subclass org.springframework.boot.context.config.ConfigFileApplicationListener
 *
 * @see org.springframework.boot.context.config.ConfigFileApplicationListener
 */
public class LocalConfigFileEnvironmentProcessor {
    private static final String DEFAULT_PROPERTIES = "defaultProperties";

    // Note the order is from least to most specific (last one wins)
    private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/";

    private static final String DEFAULT_NAMES = "application";

    /**
     * The "active profiles" property name.
     */
    public static final String ACTIVE_PROFILES_PROPERTY = "spring.profiles.active";

    /**
     * The "includes profiles" property name.
     */
    public static final String INCLUDE_PROFILES_PROPERTY = "spring.profiles.include";

    /**
     * The "config name" property name.
     */
    public static final String CONFIG_NAME_PROPERTY = "spring.config.name";

    /**
     * The "config location" property name.
     */
    public static final String CONFIG_LOCATION_PROPERTY = "spring.config.location";    // Note the order is from least to most specific (last one wins)

    /**
     * Name of the application configuration {@link PropertySource}.
     */
    public static final String APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME = "applicationConfigurationProperties";

    private final DeferredLog logger = new DeferredLog();

    private ResourceLoader resourceLoader;

    private String searchLocations;

    private String names;


    public LocalConfigFileEnvironmentProcessor() {
        this.resourceLoader = new DefaultResourceLoader(this.getClassLoader());
    }

    public void processEnvironment(ConfigurableEnvironment environment, PropertySource source) {
        if (source != null) {
            environment.getPropertySources().addFirst(source);
        }
        addPropertySources(environment, this.getResourceLoader());
        if (source != null) {
            this.addPropertySource(environment, (CompositePropertySource) source);
        }
    }

    protected void addPropertySources(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
        RandomValuePropertySource.addToEnvironment(environment);
        try {
            new Loader(environment, resourceLoader).load();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load configuration files", ex);
        }
    }

    private void addPropertySource(ConfigurableEnvironment environment, CompositePropertySource composite) {
        if (environment != null) {
            if (environment.getPropertySources() != null) {
                for (PropertySource source : environment.getPropertySources()) {
                    if (source.getSource() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) source
                                .getSource();
                        composite.addPropertySource(new MapPropertySource(source
                                .getName(), map));
                    } else if (source.getSource() instanceof List) {
                        List sourceList = (List) source.getSource();
                        for (Object src : sourceList) {
                            if (src instanceof  EnumerablePropertySource) {
                                EnumerablePropertySource eps = (EnumerablePropertySource) src;
                                composite.addPropertySource(eps);
                            }
                        }
                    }
                }
            }
        }
    }

    public ClassLoader getClassLoader() {
        return this.resourceLoader != null ? this.resourceLoader.getClassLoader() : ClassUtils.getDefaultClassLoader();
    }

    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    public void setSearchLocations(String locations) {
        Assert.hasLength(locations, "Locations must not be empty");
        this.searchLocations = locations;
    }

    public void setSearchNames(String names) {
        Assert.hasLength(names, "Names must not be empty");
        this.names = names;
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
     * Holder for {@code spring.profiles} properties.
     */
    static final class SpringProfiles {

        private List<String> active = new ArrayList<>();

        private List<String> include = new ArrayList<>();

        public List<String> getActive() {
            return this.active;
        }

        public void setActive(List<String> active) {
            this.active = active;
        }

        public List<String> getInclude() {
            return this.include;
        }

        public void setInclude(List<String> include) {
            this.include = include;
        }

        Set<Profile> getActiveProfiles() {
            return asProfileSet(this.active);
        }

        Set<Profile> getIncludeProfiles() {
            return asProfileSet(this.include);
        }

        private Set<Profile> asProfileSet(List<String> profileNames) {
            List<Profile> profiles = new ArrayList<>();
            for (String profileName : profileNames) {
                profiles.add(new Profile(profileName));
            }
            Collections.reverse(profiles);
            return new LinkedHashSet<>(profiles);
        }
    }

    /**
     * Loads candidate property sources and configures the active profiles.
     */
    private class Loader {

        private final Log logger = LocalConfigFileEnvironmentProcessor.this.logger;

        private final ConfigurableEnvironment environment;

        private final ResourceLoader resourceLoader;

        private PropertySourcesLoader propertiesLoader;

        private Queue<Profile> profiles;

        private List<Profile> processedProfiles;

        private boolean activatedProfiles;

        Loader(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
            this.environment = environment;
            this.resourceLoader = resourceLoader == null ? new DefaultResourceLoader()
                    : resourceLoader;
        }

        public void load() throws IOException {
            this.propertiesLoader = new PropertySourcesLoader();
            this.activatedProfiles = false;
            this.profiles = Collections.asLifoQueue(new LinkedList<Profile>());
            this.processedProfiles = new LinkedList<>();

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
                    } else {
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
            if (!this.environment.containsProperty(ACTIVE_PROFILES_PROPERTY)
                    && !this.environment.containsProperty(INCLUDE_PROFILES_PROPERTY)) {
                return Collections.emptySet();
            }
            // Any pre-existing active profiles set via property sources (e.g. System
            // properties) take precedence over those added in config files.
            SpringProfiles springProfiles = bindSpringProfiles(
                    this.environment.getPropertySources());
            Set<Profile> activeProfiles = new LinkedHashSet<>(
                    springProfiles.getActiveProfiles());
            activeProfiles.addAll(springProfiles.getIncludeProfiles());
            maybeActivateProfiles(activeProfiles);
            return activeProfiles;
        }

        private List<Profile> getUnprocessedActiveProfiles(
                Set<Profile> initialActiveProfiles) {
            List<Profile> unprocessedActiveProfiles = new ArrayList<>();
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
            String group = "profile=" + (profile == null ? "" : profile);
            if (!StringUtils.hasText(name)) {
                // Try to load directly from the location
                loadIntoGroup(group, location, profile);
            } else {
                // Search for a file with the given name
                for (String ext : this.propertiesLoader.getAllFileExtensions()) {
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
            Resource resource = this.resourceLoader.getResource(location);
            PropertySource<?> propertySource = null;
            StringBuilder msg = new StringBuilder();
            if (resource != null && resource.exists()) {
                String name = "applicationConfig: [" + location + "]";
                String group = "applicationConfig: [" + identifier + "]";
                propertySource = this.propertiesLoader.load(resource, group, name,
                        (profile == null ? null : profile.getName()));
                if (propertySource != null) {
                    msg.append("Loaded ");
                    handleProfileProperties(propertySource);
                } else {
                    msg.append("Skipped (empty) ");
                }
            } else {
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
            } else {
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
                } catch (IOException ex) {
                    // Use the location as the description
                }
            }
            return resourceDescription;
        }

        private void handleProfileProperties(PropertySource<?> propertySource) {
            SpringProfiles springProfiles = bindSpringProfiles(propertySource);
            maybeActivateProfiles(springProfiles.getActiveProfiles());
            addProfiles(springProfiles.getIncludeProfiles());
        }

        private SpringProfiles bindSpringProfiles(PropertySource<?> propertySource) {
            MutablePropertySources propertySources = new MutablePropertySources();
            propertySources.addFirst(propertySource);
            return bindSpringProfiles(propertySources);
        }

        private SpringProfiles bindSpringProfiles(PropertySources propertySources) {
            SpringProfiles springProfiles = new SpringProfiles();
            RelaxedDataBinder dataBinder = new RelaxedDataBinder(springProfiles,
                    "spring.profiles");
            dataBinder.bind(new PropertySourcesPropertyValues(propertySources));
//            dataBinder.bind(new PropertySourcesPropertyValues(propertySources, false));
            springProfiles.setActive(resolvePlaceholders(springProfiles.getActive()));
            springProfiles.setInclude(resolvePlaceholders(springProfiles.getInclude()));
            return springProfiles;
        }

        private List<String> resolvePlaceholders(List<String> values) {
            List<String> resolved = new ArrayList<>();
            for (String value : values) {
                resolved.add(this.environment.resolvePlaceholders(value));
            }
            return resolved;
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
                    .hasNext(); ) {
                if (iterator.next().isDefaultProfile()) {
                    iterator.remove();
                }
            }
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
            Set<String> profiles = new LinkedHashSet<>();
            environment.getActiveProfiles(); // ensure they are initialized
            // But this one should go first (last wins in a property key clash)
            profiles.add(profile.getName());
            profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
            environment.setActiveProfiles(profiles.toArray(new String[profiles.size()]));
        }

        private Set<String> getSearchLocations() {
            Set<String> locations = new LinkedHashSet<>();
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
                    asResolvedSet(LocalConfigFileEnvironmentProcessor.this.searchLocations,
                            DEFAULT_SEARCH_LOCATIONS));
            return locations;
        }

        private Set<String> getSearchNames() {
            if (this.environment.containsProperty(CONFIG_NAME_PROPERTY)) {
                return asResolvedSet(this.environment.getProperty(CONFIG_NAME_PROPERTY),
                        null);
            }
            return asResolvedSet(LocalConfigFileEnvironmentProcessor.this.names, DEFAULT_NAMES);
        }

        private Set<String> asResolvedSet(String value, String fallback) {
            List<String> list = Arrays.asList(StringUtils.trimArrayElements(
                    StringUtils.commaDelimitedListToStringArray(value != null
                            ? this.environment.resolvePlaceholders(value) : fallback)));
            Collections.reverse(list);
            return new LinkedHashSet<>(list);
        }

        private void addConfigurationProperties(MutablePropertySources sources) {
            List<PropertySource<?>> reorderedSources = new ArrayList<>();
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
            } else {
                existingSources.addLast(configurationSources);
            }
        }
    }

}
