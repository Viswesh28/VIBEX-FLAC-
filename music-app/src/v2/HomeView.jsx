import { LongPressButton } from "./LongPressButton.jsx";
import { useMemo } from "react";
import {
  Play,
  ChevronRight,
  Clock,
  Headphones,
  BarChart3,
  Sparkles,
} from "lucide-react";
import { useVibe } from "./Model.jsx";
import { artistsOf } from "../lib/song.js";
import { decodeHtml } from "../lib/format.js";
import { rankDiscovery } from "./core.js";
import {
  Cover,
  useRemote,
  Loading,
  Failure,
  Section,
  TrackList,
} from "./ui.jsx";
const text = decodeHtml;
const moods = ["All", "Feel good", "Chill", "Focus", "Workout", "Romance"];
export function HomeView({ onMenu, onNavigate, mood, setMood }) {
  const { library, settings, setSetting, playList, state, events } = useVibe();
  const query = `${settings.language} ${mood === "All" ? "hits" : mood} ${settings.genre === "All" ? "" : settings.genre}`;
  const remote = useRemote(
      `/search/songs?query=${encodeURIComponent(query)}&limit=18`,
      { persistent: true },
    ),
    albums = useRemote(
      `/search/albums?query=${encodeURIComponent(settings.language + " hits")}&limit=8`,
      { persistent: true },
    );
  const songs = useMemo(
      () => rankDiscovery(remote.data?.results || [], library, events),
      [remote.data, library, events],
    ),
    recent = library.recent.map((id) => library.songs[id]).filter(Boolean);
  return (
    <>
      <div className="greeting">
        <div>
          <span className="eyebrow">YOUR DAILY SOUNDTRACK</span>
          <h1>
            A little music.
            <br className="mobile-only" /> A better day
            <span className="accent">.</span>
          </h1>
          <p>Old favorites. New discoveries. All your vibes.</p>
        </div>
        <div className="language-select">
          <Headphones size={17} />
          <select
            aria-label="Music language"
            value={settings.language}
            onChange={(e) => setSetting("language", e.target.value)}
          >
            {[
              "Tamil",
              "Hindi",
              "English",
              "Telugu",
              "Malayalam",
              "Kannada",
              "Punjabi",
              "Bengali",
            ].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </div>
      </div>
      <div className="mood-chips">
        {moods.map((m, i) => (
          <button
            key={m}
            className={m === mood ? "selected" : ""}
            onClick={() => setMood(m)}
          >
            {i === 0 ? <Sparkles size={15} /> : null}
            {m}
          </button>
        ))}
      </div>
      {songs.length > 0 && (
        <div className="echo-feature-rail" aria-label="Featured songs">
          {songs.slice(0, 6).map((song, i) => (
            <LongPressButton key={song.id} className="echo-feature-card"
              aria-label={`Play featured ${text(song.name)}`}
              onLongPress={() => onMenu(song)}
              onClick={() => playList(songs, i)}>
              <Cover song={song} />
              <span className="echo-feature-caption"><strong>{text(song.name)}</strong><small>{text(artistsOf(song))}</small></span>
            </LongPressButton>
          ))}
        </div>
      )}
      <div className="catalog-status">
        <span>
          {remote.saved
            ? remote.offline
              ? "Offline · saved Home picks"
              : remote.stale
                ? "Saved Home picks · refresh when you’re ready"
                : "Home picks saved on this device"
            : "Home picks · saved for your next visit"}
        </span>
        <button
          className="text-button"
          disabled={remote.loading || albums.loading}
          onClick={() => {
            remote.retry();
            albums.retry();
          }}
        >
          Refresh Home
        </button>
      </div>
      {state.queue.length > 0 && !state.playing && (
        <div className="resume-card">
          <Clock size={22} />
          <div>
            <strong>Pick up where you left off</strong>
            <span>{text(state.queue[state.index]?.name || "Your queue")}</span>
          </div>
          <button
            className="small-primary"
            onClick={() =>
              onNavigate({
                kind: "player",
              })
            }
          >
            Continue <Play size={14} />
          </button>
        </div>
      )}
      {recent.length > 0 && (
        <Section
          title="Back in your rotation"
          eyebrow="RECENTLY PLAYED"
          action={
            <button
              className="text-button"
              onClick={() =>
                onNavigate({
                  kind: "library",
                  filter: "recent",
                })
              }
            >
              View all <ChevronRight size={16} />
            </button>
          }
        >
          <div className="recent-grid">
            {recent.slice(0, 6).map((s) => (
              <button
                className="recent-card"
                key={s.id}
                onClick={() =>
                  playList(
                    recent,
                    recent.findIndex((x) => x.id === s.id),
                  )
                }
              >
                <Cover song={s} />
                <span>
                  <strong>{text(s.name)}</strong>
                  <small>{text(artistsOf(s))}</small>
                </span>
                <Play size={16} />
              </button>
            ))}
          </div>
        </Section>
      )}
      <Section
        title="Your quick picks"
        eyebrow="A LITTLE SOMETHING YOU’LL LOVE"
        action={
          <button
            className="text-button"
            onClick={() => playList(songs)}
            disabled={!songs.length}
          >
            <Play size={15} /> Play all
          </button>
        }
      >
        {remote.loading ? (
          <Loading />
        ) : remote.error ? (
          <Failure remote={remote} />
        ) : (
          <TrackList songs={songs.slice(0, 6)} onMenu={onMenu} />
        )}
      </Section>
      <div className="home-mix-actions">
        <button className="home-mix-action" disabled={!songs.length} onClick={() => playList(songs)}><Play size={18} fill="currentColor" /> Play the mix <span>{songs.length} tracks</span></button>
        <button className="home-mix-action" onClick={() => onNavigate({kind:"stats"})}><BarChart3 size={18}/> Listening insights</button>
      </div>
      {albums.data?.results?.length > 0 && (
        <Section title="Dive a little deeper" eyebrow="ALBUMS TO GET LOST IN">
          <div className="album-rail">
            {albums.data.results.map((a) => (
              <LongPressButton
                className="album-card"
                key={a.id}
                onLongPress={() => onMenu({collection:true,name:a.name,type:"album",view:{kind:"detail",type:"album",id:a.id}})}
                onClick={() =>
                  onNavigate({
                    kind: "detail",
                    type: "album",
                    id: a.id,
                  })
                }
              >
                <Cover song={a} />
                <strong>{text(a.name)}</strong>
                <span>{a.year || settings.language} · Album</span>
              </LongPressButton>
            ))}
          </div>
        </Section>
      )}
      <div className="home-footer">
        <div className="brand-mark small">v</div>
        <p>
          Made for the way you listen.
          <br />
          <span>Your library stays yours. No account needed.</span>
        </p>
      </div>
    </>
  );
}
