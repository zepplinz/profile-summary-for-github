// Define a type for the environment variables
type EnvironmentVariables = { [key: string]: string | undefined };
// Define a function to get environment variables
function getEnvironmentVariable(name: string): string | undefined {
    return process.env[name.toUpperCase().replace("-", "_")];
}
// Define the Config object
const Config = {
    // Get port from Heroku, or return null (localhost)
    getPort: (): number | null => {
        const port = getEnvironmentVariable("port");
        return port ? parseInt(port) : null;
    },
    // Get 'api-tokens' from Heroku/System, or return null if not set
    getApiTokens: (): string | null => {
        return getProperty("api-tokens");
    },
    // Get 'unrestricted' state from Heroku/System, or return null if not set
    unrestricted: (): boolean => {
        return getProperty("unrestricted")?.toLowerCase() === "true";
    },
    // Get 'gtm-id' from Heroku/System, or return null if not set
    getGtmId: (): string | null => {
        return getProperty("gtm-id");
    },
    // Get 'star-bypass' from Heroku/System, or return null if not stored
    freeRequestCutoff: (): number | null => {
        const cutoff = getProperty("free-requests-cutoff");
        return cutoff ? parseInt(cutoff) : null;
    }
};
// Define a function to get properties from Heroku/System
function getProperty(name: string): string | null {
    return getEnvironmentVariable(name) || process.env[name] || null;
}