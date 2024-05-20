import { CommitCountUtil } from './util/CommitCountUtil';
import { Repository, RepositoryCommit } from 'github-api';
import { LoggerFactory } from 'slf4j';
import { ConcurrentHashMap } from 'concurrent-hashmap';
import { IntStream } from 'stream';
import { CacheService } from './CacheService';
import { Config } from './Config';
import { GhService } from './GhService';

type UserProfile = {
    user: any;
    quarterCommitCount: any;
    langRepoCount: Map<string, number>;
    langStarCount: Map<string, number>;
    langCommitCount: Map<string, number>;
    repoCommitCount: Map<string, number>;
    repoStarCount: Map<string, number>;
    repoCommitCountDescriptions: Map<string, string | undefined>;
    repoStarCountDescriptions: Map<string, string | undefined>;
};

class UserService {
    private static readonly pageSize = 100;
    private static readonly log = LoggerFactory.getLogger('app.UserCtrlKt');
    private static readonly repo = GhService.repos.getRepository('tipsy', 'profile-summary-for-github');
    private static readonly watchers = new Set<string>();
    private static readonly freeRequestCutoff = Config.freeRequestCutoff();

    static userExists(user: string): boolean {
        try {
            return GhService.users.getUser(user) !== null;
        } catch (e) {
            return false;
        }
    }

    private static remainingRequests(): number {
        return GhService.remainingRequests;
    }

    private static hasFreeRemainingRequests(): boolean {
        return this.remainingRequests() > (this.freeRequestCutoff ?? this.remainingRequests());
    }

    static canLoadUser(user: string): boolean {
        const userCacheJson = CacheService.selectJsonFromDb(user);
        return Config.unrestricted()
            || userCacheJson !== null
            || this.hasFreeRemainingRequests()
            || (this.remainingRequests() > 0 && this.hasStarredRepo(user));
    }

    static getUserIfCanLoad(username: string): UserProfile | null {
        const userCacheJson = CacheService.selectJsonFromDb(username);
        const canLoadUser = Config.unrestricted()
            || userCacheJson !== null
            || this.hasFreeRemainingRequests()
            || (this.remainingRequests() > 0 && this.hasStarredRepo(username));

        if (canLoadUser) {
            if (userCacheJson === null) {
                return this.generateUserProfile(username);
            } else {
                return CacheService.getUserFromJson(userCacheJson);
            }
        }

        return null;
    }

    private static hasStarredRepo(username: string): boolean {
        const login = username.toLowerCase();
        if (this.watchers.has(login)) return true;
        this.syncWatchers();
        return this.watchers.has(login);
    }

    static syncWatchers() {
        const realWatchers = this.repo.watchers;
        if (this.watchers.size < realWatchers) {
            const startPage = Math.floor(this.watchers.size / this.pageSize) + 1;
            const lastPage = Math.floor(realWatchers / this.pageSize) + 1;
            if (startPage === lastPage) {
                this.addAllWatchers(lastPage);
            } else {
                IntStream.rangeClosed(startPage, lastPage).parallel().forEach(page => this.addAllWatchers(page));
            }
        }
    }

    private static addAllWatchers(pageNumber: number) {
        try {
            GhService.watchers.pageWatchers(this.repo, pageNumber, this.pageSize).first().forEach(watcher => {
                this.watchers.add(watcher.login.toLowerCase());
            });
        } catch (e) {
            this.log.info('Exception while adding watchers', e);
        }
    }

    private static commitsForRepo(repo: Repository): RepositoryCommit[] {
        try {
            return GhService.commits.getCommits(repo);
        } catch (e) {
            return [];
        }
    }

    private static generateUserProfile(username: string): UserProfile {
        const user = GhService.users.getUser(username);
        const repos = GhService.repos.getRepositories(username).filter(repo => !repo.isFork && repo.size !== 0);
        const repoCommits = new Map(repos.map(repo => [repo, this.commitsForRepo(repo).filter(commit => commit.author?.login.toLowerCase() === username.toLowerCase())]));
        const langRepoGrouping = new Map(repos.map(repo => [repo.language ?? 'Unknown', repo]));

        const quarterCommitCount = CommitCountUtil.getCommitsForQuarters(user, repoCommits);
        const langRepoCount = new Map([...langRepoGrouping.entries()].map(([lang, repos]) => [lang, repos.length]).sort((a, b) => b[1] - a[1]));
        const langStarCount = new Map([...langRepoGrouping.entries()].map(([lang, repos]) => [lang, repos.reduce((acc, repo) => acc + repo.watchers, 0)]).filter(([_, count]) => count > 0).sort((a, b) => b[1] - a[1]));
        const langCommitCount = new Map([...langRepoGrouping.entries()].map(([lang, repos]) => [lang, repos.reduce((acc, repo) => acc + (repoCommits.get(repo)?.length ?? 0), 0)]).sort((a, b) => b[1] - a[1]));
        const repoCommitCount = new Map([...repoCommits.entries()].map(([repo, commits]) => [repo.name, commits.length]).sort((a, b) => b[1] - a[1]).slice(0, 10));
        const repoStarCount = new Map(repos.filter(repo => repo.watchers > 0).map(repo => [repo.name, repo.watchers]).sort((a, b) => b[1] - a[1]).slice(0, 10));

        const repoCommitCountDescriptions = new Map([...repoCommitCount.entries()].map(([name, _]) => [name, repos.find(repo => repo.name === name)?.description]));
        const repoStarCountDescriptions = new Map([...repoStarCount.entries()].map(([name, _]) => [name, repos.find(repo => repo.name === name)?.description]));

        const userProfile: UserProfile = {
            user,
            quarterCommitCount,
            langRepoCount,
            langStarCount,
            langCommitCount,
            repoCommitCount,
            repoStarCount,
            repoCommitCountDescriptions,
            repoStarCountDescriptions
        };

        CacheService.saveInCache(userProfile);

        return userProfile;
    }
}

export { UserService, UserProfile };