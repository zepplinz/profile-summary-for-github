// Define the User type based on the assumed structure from the Kotlin code
interface User {
    // Add properties as needed, for example:
    id: number;
    login: string;
    avatar_url: string;
    // Add other properties as needed
}
// Define the UserProfile type
interface UserProfile {
    user: User;
    quarterCommitCount: Record<string, number>;
    langRepoCount: Record<string, number>;
    langStarCount: Record<string, number>;
    langCommitCount: Record<string, number>;
    repoCommitCount: Record<string, number>;
    repoStarCount: Record<string, number>;
    repoCommitCountDescriptions: Record<string, string | null>;
    repoStarCountDescriptions: Record<string, string | null>;
}