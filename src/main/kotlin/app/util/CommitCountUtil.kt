import { Repository, RepositoryCommit, User } from 'some-github-library';
import { Instant, OffsetDateTime, ZoneOffset, TemporalAdjusters, IsoFields } from 'some-time-library';

type SortedMap<K, V> = Map<K, V>;

class CommitCountUtil {

    static getCommitsForQuarters(user: User, repoCommits: Map<Repository, RepositoryCommit[]>): SortedMap<string, number> {
        const creation = this.asInstant(user.createdAt).withDayOfMonth(1);
        const now = Instant.now().atOffset(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfNextMonth());

        const quarterBuckets = new Map<string, number>(
            Array.from({ length: IsoFields.QUARTER_YEARS.between(creation, now) + 1 }, (_, i) => [
                this.yearQuarterFromDate(creation.plus(i, IsoFields.QUARTER_YEARS)),
                0
            ])
        );

        repoCommits.forEach(commits => {
            commits.forEach(commit => {
                const key = this.yearQuarterFromCommit(commit);
                quarterBuckets.set(key, (quarterBuckets.get(key) || 0) + 1);
            });
        });

        return new Map([...quarterBuckets.entries()].sort());
    }

    private static asInstant(date: Date): OffsetDateTime {
        return OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
    }

    private static yearQuarterFromCommit(commit: RepositoryCommit): string {
        return this.yearQuarterFromDate(this.asInstant(commit.commit.committer.date));
    }

    private static yearQuarterFromDate(date: OffsetDateTime): string {
        return `${date.year}-Q${date.get(IsoFields.QUARTER_OF_YEAR)}`;
    }
}