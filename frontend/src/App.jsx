
import { useEffect, useMemo, useState } from "react";
import "./App.css";

const OPEN = 9 * 60;
const CLOSE = 21 * 60;
const STEP = 10;
// 현재 백엔드는 로그인 연동 전 단계: 개발용 사용자 ID 1
const DEMO_USER_ID = 1;
// 실제 DB에 등록된 공간만 예약할 수 있습니다.
const CONNECTED_SPACE_IDS = [1];

async function api(path, options = {}) {
  let response;
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      headers: { "Content-Type": "application/json", ...options.headers },
    });
  } catch {
    throw new Error("Spring Boot 서버에 연결할 수 없습니다. 8080 포트를 확인하세요.");
  }
  if (!response.ok) {
    const messages = {
      400: "예약 날짜와 시간을 다시 확인해 주세요.",
      403: "본인의 예약만 변경하거나 취소할 수 있습니다.",
      404: "해당 공간 또는 예약을 찾을 수 없습니다.",
      409: "이미 예약된 시간이거나 다른 예약과 충돌했습니다.",
    };
    throw new Error(messages[response.status] || `서버 요청 실패 (HTTP ${response.status})`);
  }
  if (response.status === 204) return null;
  return response.json();
}

function minutesFromClock(time) {
  const [h, m] = String(time).slice(0, 5).split(":").map(Number);
  return h * 60 + m;
}
const isOccupied = (b) =>
  b.status === "PENDING_PAYMENT" || b.status === "CONFIRMED";

function fromServerReservation(r) {
  const startAt = r.startAt;
  const endAt = r.endAt;
  return {
    id: r.reservationId ?? r.id,
    roomId: Number(r.spaceId ?? r.space?.id),
    day: startAt.slice(0, 10),
    start: minutesFromClock(startAt.slice(11)),
    end: minutesFromClock(endAt.slice(11)),
    mine: true,
    status: r.effectiveStatus ?? r.status,
    expiresAt: r.expiresAt ?? null,
    purpose: "예약",
  };
}
function combineBusySlots(slots, roomId, day, mine) {
  const sorted = [...(slots || [])]
    .map(slot => ({start: minutesFromClock(slot.start), end: minutesFromClock(slot.end)}))
    .filter(slot => !mine.some(b => b.roomId === roomId && b.day === day && isOccupied(b) && overlaps(slot, b)))
    .sort((a, b) => a.start - b.start);
  const merged = [];
  for (const slot of sorted) {
    const last = merged[merged.length - 1];
    if (last && last.end >= slot.start) last.end = Math.max(last.end, slot.end);
    else merged.push({...slot});
  }
  return merged.map((r, i) => ({
    id: `occupied-${roomId}-${day}-${i}`, roomId, day,
    start: r.start, end: r.end, mine: false, status: "CONFIRMED",
  }));
}

const ROOMS = [
  {
    id: 1,
    name: "회의실 A",
    location: "본관 3층",
    capacity: 6,
    type: "회의실",
    color: "#8fb987",
    description: "팀 회의와 소규모 미팅에 적합한 공간",
    features: ["모니터", "화이트보드", "Wi-Fi"],
  },
  {
    id: 2,
    name: "크리에이티브 라운지",
    location: "신관 5층",
    capacity: 12,
    type: "라운지",
    color: "#a1c9b8",
    description: "워크숍과 자유로운 아이디어 회의를 위한 공간",
    features: ["프로젝터", "소파", "Wi-Fi"],
  },
  {
    id: 3,
    name: "미팅룸 B",
    location: "본관 2층",
    capacity: 4,
    type: "회의실",
    color: "#bdc59a",
    description: "인터뷰 및 집중 회의에 알맞은 공간",
    features: ["모니터", "Wi-Fi"],
  },
  {
    id: 4,
    name: "컨퍼런스 홀",
    location: "본관 1층",
    capacity: 24,
    type: "행사장",
    color: "#d6bea5",
    description: "세미나와 발표를 진행할 수 있는 넓은 공간",
    features: ["대형 스크린", "음향", "프로젝터"],
  },
];

const MENU = [
  { id: "home", icon: "⌂", label: "대시보드" },
  { id: "booking", icon: "▦", label: "공간 예약" },
  { id: "spaces", icon: "▤", label: "공간 둘러보기" },
  { id: "mine", icon: "▣", label: "나의 예약" },
  { id: "stats", icon: "◫", label: "이용 현황" },
  { id: "settings", icon: "⚙", label: "설정" },
];

const pad = (n) => String(n).padStart(2, "0");

const toTime = (minutes) =>
  `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;

const toDate = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

const addDays = (day, amount) => {
  const date = new Date(`${day}T12:00:00`);
  date.setDate(date.getDate() + amount);
  return toDate(date);
};

const durationText = (start, end) => {
  const minutes = end - start;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;

  if (!hours) return `${rest}분`;
  if (!rest) return `${hours}시간`;
  return `${hours}시간 ${rest}분`;
};

const overlaps = (a, b) =>
  a.start < b.end && b.start < a.end;

const isFuture = (day, start) =>
  new Date(`${day}T${toTime(start)}:00`).getTime() > Date.now();

const OPTIONS = Array.from(
  { length: (CLOSE - OPEN) / STEP + 1 },
  (_, i) => OPEN + i * STEP
);

const tomorrow = addDays(toDate(new Date()), 1);

function RoomIcon({ room }) {
  return (
    <div
      className="room-icon"
      style={{ backgroundColor: room.color }}
    >
      {room.id === 4 ? "▥" : "▦"}
    </div>
  );
}

function ReservationTimeline({
  room,
  bookings,
  day,
  onSelect,
}) {
  const items = bookings.filter(
    (b) =>
      b.roomId === room.id &&
      b.day === day &&
      isOccupied(b)
  );

  const handleClick = (event) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const ratio = Math.max(
      0,
      Math.min(0.99999, (event.clientX - rect.left) / rect.width)
    );

    const start =
      OPEN +
      Math.floor(
        (ratio * (CLOSE - OPEN)) / STEP
      ) * STEP;

    onSelect(room.id, start);
  };

  return (
    <div className="timeline-row">
      <div className="timeline-room">
        <RoomIcon room={room} />
        <div>
          <strong>{room.name}</strong>
          <small>
            {room.capacity}명 · {room.location}
          </small>
        </div>
      </div>

      <div
        className="timeline-track"
        onClick={handleClick}
        title="빈 시간대를 클릭하여 예약"
      >
        {Array.from({ length: 13 }, (_, i) => (
          <span
            key={i}
            className="timeline-line"
            style={{ left: `${(i / 12) * 100}%` }}
          />
        ))}

        {items.map((item) => (
          <div
            key={item.id}
            className={
              item.mine
                ? "timeline-block mine-block"
                : "timeline-block busy-block"
            }
            style={{
              left: `${
                ((item.start - OPEN) / (CLOSE - OPEN)) * 100
              }%`,
              width: `${
                ((item.end - item.start) /
                  (CLOSE - OPEN)) *
                100
              }%`,
            }}
            title={`${toTime(item.start)} ~ ${toTime(item.end)}`}
          />
        ))}
      </div>

      <button
        className="small-outline"
        onClick={() => onSelect(room.id)}
      >
        예약
      </button>
    </div>
  );
}

export default function App() {
  const [page, setPage] = useState("booking");
  const [day, setDay] = useState(tomorrow);
  const [bookings, setBookings] = useState([]);
  const [myServerBookings, setMyServerBookings] = useState([]);
  const [syncVersion, setSyncVersion] = useState(0);
  const [apiReady, setApiReady] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [roomId, setRoomId] = useState(1);
  const [start, setStart] = useState(610);
  const [end, setEnd] = useState(670);
  const [purpose, setPurpose] = useState("팀 회의");
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("전체");
  const [modalOpen, setModalOpen] = useState(false);
  const [editingBooking, setEditingBooking] = useState(null);
  const [cancelTarget, setCancelTarget] = useState(null);
  const [notice, setNotice] = useState("");
  const [showHelp, setShowHelp] = useState(false);

  useEffect(() => {
    let live = true;
    setLoading(true);
    setApiReady(false);
    async function loadFromBackend() {
      try {
        const raw = await api(`/reservations/users/${DEMO_USER_ID}`);
        const list = Array.isArray(raw) ? raw : raw.reservations ?? raw.items ?? [];
        const mine = list.map(fromServerReservation);
        const availabilities = await Promise.all(
          CONNECTED_SPACE_IDS.map(id => api(`/reservations/spaces/${id}/availability?date=${day}`))
        );
        const busy = availabilities.flatMap((a, index) =>
          combineBusySlots(a.reservedSlots, CONNECTED_SPACE_IDS[index], day, mine)
        );
        if (!live) return;
        setMyServerBookings(mine);
        setBookings([...mine, ...busy]);
        setApiReady(true);
      } catch (error) {
        if (!live) return;
        setBookings([]);
        setApiReady(false);
        setNotice(error.message + " 예약 기능을 사용할 수 없습니다.");
      } finally {
        if (live) setLoading(false);
      }
    }
    loadFromBackend();
    return () => {live = false;};
  }, [day, syncVersion]);

  const refreshBookings = () => setSyncVersion(n => n + 1);

  const room = ROOMS.find((r) => r.id === roomId);

  const activeBookings = bookings.filter(
    isOccupied
  );

  const myBookings = useMemo(
    () =>
      myServerBookings
        .filter(
          (b) => b.mine && ["PENDING_PAYMENT", "CONFIRMED"].includes(b.status)
        )
        .sort((a, b) =>
          `${a.day}${pad(a.start)}`.localeCompare(
            `${b.day}${pad(b.start)}`
          )
        ),
    [myServerBookings]
  );

  const cancelledBookings = myServerBookings.filter(
    (b) => b.status === "CANCELLED" || b.status === "EXPIRED"
  );

  const visibleRooms = ROOMS.filter((r) => {
    const matchesSearch =
      `${r.name} ${r.location}`
        .toLowerCase()
        .includes(search.toLowerCase());

    const matchesFilter =
      filter === "전체" || r.type === filter;

    return matchesSearch && matchesFilter;
  });

  const validRange =
    start >= OPEN &&
    end <= CLOSE &&
    start < end &&
    start % STEP === 0 &&
    end % STEP === 0;

  const conflict = activeBookings.find(
    (b) =>
      b.roomId === roomId &&
      b.day === day &&
      // 예약 변경 시 자기 자신의 시간은 중복 검사에서 제외
      (!editingBooking || String(b.id) !== String(editingBooking.id)) &&
      overlaps({ start, end }, b)
  );

  const canReserve =
    validRange &&
    isFuture(day, start) &&
    !conflict &&
    apiReady &&
    !loading &&
    !saving &&
    CONNECTED_SPACE_IDS.includes(roomId);

  const todayBookings = activeBookings.filter(
    (b) => b.day === day
  );

  const showMessage = (message) => {
    setNotice(message);
  };

  const goPage = (nextPage) => {
    setPage(nextPage);
    setModalOpen(false);
    setCancelTarget(null);
    setEditingBooking(null);
  };

  const openReservation = (
    nextRoomId = roomId,
    nextStart = null
  ) => {
    const selectedRoom = Number(nextRoomId);
    if (!CONNECTED_SPACE_IDS.includes(selectedRoom)) {
      showMessage("이 공간은 아직 DB에 등록되지 않았습니다. 현재 회의실 A만 예약할 수 있습니다.");
      return;
    }
    let selectedStart =
      nextStart === null ? 610 : nextStart;

    selectedStart = Math.max(
      OPEN,
      Math.min(CLOSE - STEP, selectedStart)
    );

    setRoomId(selectedRoom);
    setStart(selectedStart);
    setEnd(
      Math.min(CLOSE, selectedStart + 60)
    );
    setPurpose("팀 회의");
    setEditingBooking(null);
    refreshBookings();
    setModalOpen(true);
  };

  const openEditReservation = (booking) => {
    if (booking.status !== "PENDING_PAYMENT") {
      showMessage("현재 상태에서는 예약을 변경할 수 없습니다.");
      return;
    }
    setEditingBooking(booking);
    setRoomId(booking.roomId);
    setDay(booking.day);
    setStart(booking.start);
    setEnd(booking.end);
    setPurpose(booking.purpose || "회의");
    setModalOpen(true);
  };

  const createReservation = async () => {
    if (!canReserve) {
      showMessage("예약 가능 여부 및 서버 연결 상태를 확인해 주세요.");
      return;
    }
    setSaving(true);
    try {
      if (editingBooking) {
        await api(`/reservations/${editingBooking.id}`, {
          method: "PATCH",
          body: JSON.stringify({
            userId: DEMO_USER_ID,
            startAt: `${day}T${toTime(start)}:00`,
            endAt: `${day}T${toTime(end)}:00`,
          }),
        });
      } else {
        await api("/reservations", {
          method: "POST",
          body: JSON.stringify({
            userId: DEMO_USER_ID,
            spaceId: roomId,
            startAt: `${day}T${toTime(start)}:00`,
            endAt: `${day}T${toTime(end)}:00`,
          }),
        });
      }
      const wasEditing = Boolean(editingBooking);
      setModalOpen(false);
      setEditingBooking(null);
      setPage("mine");
      refreshBookings();
      showMessage(`${room.name} ${toTime(start)}~${toTime(end)} ${wasEditing ? "예약 변경" : "예약 생성"}이 서버에 저장되었습니다.`);
    } catch (error) {
      refreshBookings();
      showMessage(error.message);
    } finally {
      setSaving(false);
    }
  };

  const cancelReservation = async () => {
    if (!cancelTarget || saving) return;
    setSaving(true);
    try {
      await api(`/reservations/${cancelTarget.id}/cancel?userId=${DEMO_USER_ID}`, {
        method: "PATCH",
      });
      setCancelTarget(null);
      refreshBookings();
      showMessage("예약 취소가 서버에 저장되었습니다. 해당 시간이 다시 예약 가능해집니다.");
    } catch (error) {
      showMessage(error.message);
    } finally {
      setSaving(false);
    }
  };

  const roomUsage = (targetRoomId) =>
    todayBookings
      .filter((b) => b.roomId === targetRoomId)
      .reduce(
        (sum, b) => sum + b.end - b.start,
        0
      );

  const resetDemo = () => {
    refreshBookings();
    showMessage("서버 예약 정보를 다시 불러왔습니다. 실제 예약은 삭제되지 않습니다.");
  };

  const PAGE_TITLES = {
    home: "대시보드",
    booking: "공간 예약",
    spaces: "공간 둘러보기",
    mine: "나의 예약",
    stats: "이용 현황",
    settings: "설정",
  };

  return (
    <div className="app-shell">
      {/* 사이드바 */}
      <aside className="sidebar">
        <button
          className="brand"
          onClick={() => goPage("home")}
        >
          <span className="brand-symbol">◩</span>
          <span>
            <strong>OneSlot</strong>
            <small>Space for your time</small>
          </span>
        </button>

        <div className="workspace">
          <span className="workspace-avatar">O</span>
          <div>
            <strong>OneSlot Workspace</strong>
            <small>공간 예약 서비스</small>
          </div>
          <span className="workspace-check">✓</span>
        </div>

        <div className="menu-label">
          WORKSPACE
        </div>

        <nav className="sidebar-menu">
          {MENU.map((menu) => (
            <button
              key={menu.id}
              className={
                page === menu.id ? "active" : ""
              }
              onClick={() => goPage(menu.id)}
            >
              <span className="menu-icon">
                {menu.icon}
              </span>
              {menu.label}

              {menu.id === "mine" &&
                myBookings.length > 0 && (
                  <span className="menu-count">
                    {myBookings.length}
                  </span>
                )}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="sidebar-promo">
            <div className="promo-symbol">▦</div>
            <h3>필요한 공간, 필요한 만큼</h3>
            <p>
              10분 단위로 자유롭게
              <br />
              시간을 선택하세요.
            </p>
            <button
              onClick={() => {
                goPage("booking");
                openReservation();
              }}
            >
              예약하러 가기 →
            </button>
          </div>

          <div className="profile">
            <span className="profile-avatar">ON</span>
            <div>
              <strong>OneSlot 사용자</strong>
              <small>체험용 계정</small>
            </div>
          </div>
        </div>
      </aside>

      {/* 메인 */}
      <div className="main-area">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace
            <span>›</span>
            <strong>{PAGE_TITLES[page]}</strong>
          </div>

          <div className="topbar-right">
            <span className="demo-badge">
              ● API 연결 모드 · 사용자 1
            </span>

            <button
              className="icon-button"
              title="도움말"
              onClick={() =>
                setShowHelp(!showHelp)
              }
            >
              ?
            </button>

            <span className="top-avatar">ON</span>
          </div>
        </header>

        <main className="main-content">


          {!apiReady && (
            <div className="help-banner">
              <strong>{loading ? "서버 예약 데이터 불러오는 중" : "서버 연결 확인 필요"}</strong>
              <span>Spring Boot(8080) 실행 및 Vite /api 프록시를 확인하세요. 연결 전에는 예약할 수 없습니다.</span>
              <button onClick={refreshBookings}>다시 시도</button>
            </div>
          )}
          <div className="page-heading">
            <div>
              <div className="eyebrow">
                ONESLOT WORKSPACE
              </div>
              <h1>{PAGE_TITLES[page]}</h1>
              <p>
                {page === "booking"
                  ? "원하는 공간과 이용 시간을 직접 선택해 보세요."
                  : page === "mine"
                  ? "예약 내역을 확인하고 취소할 수 있습니다."
                  : "효율적인 공간 이용을 위한 OneSlot 서비스"}
              </p>
            </div>

            <button
              className="primary-button"
              onClick={() => {
                goPage("booking");
                openReservation();
              }}
            >
              + 새 예약
            </button>
          </div>

          {/* 홈 */}
          {(page === "home" ||
            page === "booking") && (
            <>
              <section className="hero-card">
                <div className="hero-text">
                  <span className="hero-tag">
                    SMART SPACE BOOKING
                  </span>
                  <h2>
                    공간은 자유롭게,
                    <br />
                    시간은 필요한 만큼.
                  </h2>
                  <p>
                    1시간 단위가 아닌 10분 단위로
                    <br />
                    시작과 종료 시간을 직접 선택하세요.
                  </p>
                  <button
                    onClick={() =>
                      openReservation()
                    }
                  >
                    원하는 시간 예약하기 →
                  </button>
                </div>

                <div className="hero-visual">
                  <div className="visual-circle" />
                  <div className="visual-card">
                    <span>✓ 예약 예시</span>
                    <strong>
                      09:00 — 10:10
                    </strong>
                    <small>
                      회의실 A · 1시간 10분
                    </small>
                  </div>
                  <div className="visual-tag">
                    10분 단위 자유 예약 ✦
                  </div>
                </div>
              </section>

              <section className="summary-grid">
                <div className="summary-card">
                  <span className="summary-icon">▦</span>
                  <div>
                    <small>운영 중인 공간</small>
                    <strong>{ROOMS.length}개</strong>
                  </div>
                </div>

                <div className="summary-card">
                  <span className="summary-icon">▣</span>
                  <div>
                    <small>선택 날짜 예약</small>
                    <strong>
                      {todayBookings.length}건
                    </strong>
                  </div>
                </div>

                <div className="summary-card">
                  <span className="summary-icon">✓</span>
                  <div>
                    <small>나의 예약</small>
                    <strong>
                      {myBookings.length}건
                    </strong>
                  </div>
                </div>
              </section>
            </>
          )}

          {/* 예약 시간표 */}
          {(page === "home" ||
            page === "booking") && (
            <section className="content-card">
              <div className="section-header">
                <div>
                  <span className="eyebrow">
                    AVAILABILITY
                  </span>
                  <h2>공간별 예약 현황</h2>
                  <p>
                    초록색은 서버에 저장된 예약 시간입니다.
                    빈 공간을 클릭하면 예약할 수 있어요.
                  </p>
                </div>

                <button
                  className="text-button"
                  onClick={() =>
                    goPage("mine")
                  }
                >
                  나의 예약 보기 →
                </button>
              </div>

              <div className="toolbar">
                <div className="date-controls">
                  <button
                    onClick={() =>
                      setDay(addDays(day, -1))
                    }
                  >
                    ‹
                  </button>

                  <input
                    type="date"
                    value={day}
                    min={toDate(new Date())}
                    onChange={(e) =>
                      setDay(e.target.value)
                    }
                  />

                  <button
                    onClick={() =>
                      setDay(addDays(day, 1))
                    }
                  >
                    ›
                  </button>

                  <button
                    className="today-btn"
                    onClick={() =>
                      setDay(toDate(new Date()))
                    }
                  >
                    오늘
                  </button>
                </div>

                <div className="search-controls">
                  <input
                    placeholder="공간 이름 또는 위치 검색"
                    value={search}
                    onChange={(e) =>
                      setSearch(e.target.value)
                    }
                  />

                  <select
                    value={filter}
                    onChange={(e) =>
                      setFilter(e.target.value)
                    }
                  >
                    <option value="전체">전체</option>
                    <option value="회의실">
                      회의실
                    </option>
                    <option value="라운지">
                      라운지
                    </option>
                    <option value="행사장">
                      행사장
                    </option>
                  </select>
                </div>
              </div>

              <div className="timeline-scroll">
                <div className="timeline-header">
                  <span>공간 / 시간</span>

                  <div className="timeline-hours">
                    {Array.from(
                      { length: 13 },
                      (_, i) => (
                        <span key={i}>
                          {pad(9 + i)}:00
                        </span>
                      )
                    )}
                  </div>

                  <span />
                </div>

                {visibleRooms.map((r) => (
                  <ReservationTimeline
                    key={r.id}
                    room={r}
                    bookings={bookings}
                    day={day}
                    onSelect={openReservation}
                  />
                ))}

                {visibleRooms.length === 0 && (
                  <div className="empty-message">
                    검색 결과가 없습니다.
                  </div>
                )}
              </div>

              <div className="timeline-legend">
                <span>
                  <i className="legend-dot busy" />
                  예약 중
                </span>

                <span>
                  <i className="legend-dot my" />
                  내 예약
                </span>

                <span>
                  <i className="legend-dot free" />
                  예약 가능
                </span>

                <span className="legend-note">
                  운영 시간 09:00~21:00
                  · 10분 단위
                </span>
              </div>
            </section>
          )}

          {/* 공간 목록 */}
          {(page === "home" ||
            page === "booking" ||
            page === "spaces") && (
            <section className="content-card">
              <div className="section-header">
                <div>
                  <span className="eyebrow">
                    FIND YOUR SPACE
                  </span>
                  <h2>
                    {page === "spaces"
                      ? "모든 공간 둘러보기"
                      : "추천 공간"}
                  </h2>
                  <p>
                    목적에 맞는 공간을 선택하고
                    원하는 시간을 예약하세요.
                  </p>
                </div>

                {page !== "spaces" && (
                  <button
                    className="text-button"
                    onClick={() =>
                      goPage("spaces")
                    }
                  >
                    전체 보기 →
                  </button>
                )}
              </div>

              <div className="room-grid">
                {ROOMS.map((r) => (
                  <article
                    className="room-card"
                    key={r.id}
                  >
                    <div
                      className="room-picture"
                      style={{
                        backgroundColor:
                          r.color,
                      }}
                    >
                      <div className="picture-window" />
                      <div className="picture-table" />
                      <div className="picture-chair one" />
                      <div className="picture-chair two" />
                      <span className="picture-badge">
                        {r.type}
                      </span>
                    </div>

                    <div className="room-body">
                      <div className="room-title-row">
                        <h3>{r.name}</h3>
                        <span>
                          ♙ {r.capacity}명
                        </span>
                      </div>

                      <p className="room-location">
                        ⌖ {r.location}
                      </p>

                      <p className="room-description">
                        {r.description}
                      </p>

                      <div className="feature-list">
                        {r.features.map((f) => (
                          <span key={f}>
                            {f}
                          </span>
                        ))}
                      </div>

                      <button
                        onClick={() => {
                          goPage("booking");
                          openReservation(r.id);
                        }}
                      >
                        {CONNECTED_SPACE_IDS.includes(r.id) ? "시간 선택하고 예약 →" : "서버 공간 등록 필요"}
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          )}

          {/* 나의 예약 */}
          {page === "mine" && (
            <section className="content-card">
              <div className="section-header">
                <div>
                  <span className="eyebrow">
                    MY RESERVATIONS
                  </span>
                  <h2>예약 내역</h2>
                  <p>
                    예약을 취소하면 해당 시간이
                    다시 예약 가능해집니다.
                  </p>
                </div>

                <button
                  className="text-button"
                  onClick={() =>
                    goPage("booking")
                  }
                >
                  + 예약 추가
                </button>
              </div>

              {myBookings.length === 0 ? (
                <div className="empty-state">
                  <div>▦</div>
                  <h3>아직 예약 내역이 없어요.</h3>
                  <p>
                    필요한 시간만큼 자유롭게
                    예약해 보세요.
                  </p>
                  <button
                    className="primary-button"
                    onClick={() =>
                      goPage("booking")
                    }
                  >
                    공간 예약하기
                  </button>
                </div>
              ) : (
                <div className="reservation-list">
                  {myBookings.map((b) => {
                    const targetRoom =
                      ROOMS.find(
                        (r) =>
                          r.id === b.roomId
                      );

                    return (
                      <div
                        className="reservation-item"
                        key={b.id}
                      >
                        <RoomIcon
                          room={targetRoom}
                        />

                        <div className="reservation-info">
                          <strong>
                            {targetRoom.name}
                          </strong>

                          <span>
                            {b.day}
                            {" · "}
                            {toTime(b.start)}
                            ~
                            {toTime(b.end)}
                          </span>

                          <small>
                            {durationText(
                              b.start,
                              b.end
                            )}
                            {" · "}
                            {b.purpose}
                          </small>
                        </div>

                        <span className="status-label">
                          {b.status === "PENDING_PAYMENT" ? "결제 대기"
                            : b.status === "CONFIRMED" ? "예약 확정"
                            : b.status === "EXPIRED" ? "예약 만료"
                            : b.status === "COMPLETED" ? "이용 완료" : b.status}
                        </span>

                        <button
                          className="small-outline"
                          disabled={saving || b.status !== "PENDING_PAYMENT"}
                          onClick={() => openEditReservation(b)}
                        >
                          예약 변경
                        </button>

                        <button
                          className="cancel-button"
                          onClick={() =>
                            setCancelTarget(b)
                          }
                        >
                          예약 취소
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}

              {cancelledBookings.length > 0 && (
                <div className="cancelled-section">
                  <h3>취소·만료된 예약</h3>

                  {cancelledBookings.map((b) => (
                    <div
                      className="cancelled-item"
                      key={b.id}
                    >
                      <span>
                        {
                          ROOMS.find(
                            (r) =>
                              r.id === b.roomId
                          )?.name
                        }
                        {" · "}
                        {b.day}
                        {" · "}
                        {toTime(b.start)}
                        ~
                        {toTime(b.end)}
                      </span>
                      <strong>{b.status === "EXPIRED" ? "예약 만료" : "취소 완료"}</strong>
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          {/* 이용 현황 */}
          {page === "stats" && (
            <section className="content-card">
              <div className="section-header">
                <div>
                  <span className="eyebrow">
                    SPACE INSIGHTS
                  </span>
                  <h2>공간 이용 현황</h2>
                  <p>
                    선택한 날짜의 예약 시간을
                    확인할 수 있습니다.
                  </p>
                </div>

                <input
                  type="date"
                  value={day}
                  onChange={(e) =>
                    setDay(e.target.value)
                  }
                  className="stats-date"
                />
              </div>

              <div className="usage-list">
                {ROOMS.map((r) => {
                  const used =
                    roomUsage(r.id);
                  const percent = Math.round(
                    (used / (CLOSE - OPEN)) *
                      100
                  );

                  return (
                    <div
                      className="usage-item"
                      key={r.id}
                    >
                      <div className="usage-name">
                        <strong>{r.name}</strong>
                        <small>
                          {durationText(
                            0,
                            used
                          )}{" "}
                          예약됨
                        </small>
                      </div>

                      <div className="usage-track">
                        <div
                          style={{
                            width: `${percent}%`,
                            backgroundColor:
                              r.color,
                          }}
                        />
                      </div>

                      <strong>{percent}%</strong>
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          {/* 설정 */}
          {page === "settings" && (
            <section className="content-card">
              <div className="section-header">
                <div>
                  <span className="eyebrow">
                    BOOKING RULES
                  </span>
                  <h2>예약 서비스 설정</h2>
                  <p>
                    현재 데모 버전의 운영 기준입니다.
                  </p>
                </div>
              </div>

              <div className="setting-grid">
                <div>
                  <span>◷</span>
                  <strong>운영 시간</strong>
                  <small>09:00~21:00</small>
                </div>

                <div>
                  <span>▦</span>
                  <strong>예약 단위</strong>
                  <small>10분 간격</small>
                </div>

                <div>
                  <span>✓</span>
                  <strong>중복 예약</strong>
                  <small>겹치는 시간 차단</small>
                </div>

                <div>
                  <span>×</span>
                  <strong>예약 취소</strong>
                  <small>나의 예약에서 취소</small>
                </div>
              </div>

              <div className="setting-alert">
                  <strong>백엔드 API 연동 안내</strong>

                <p>
                  예약 생성·조회·취소는 MariaDB에 저장됩니다.
                  현재 사용자 ID는 개발용 1번으로 고정되어 있고,
                  결제·환불 및 로그인 연동은 아직 진행 중입니다.
                </p>

                <button
                  className="small-outline"
                  onClick={resetDemo}
                >
                  서버 데이터 새로고침
                </button>
              </div>
            </section>
          )}

          <footer className="footer">
            <span>© 2026 OneSlot</span>
            <span>
              Space for your time
            </span>
          </footer>
        </main>
      </div>

{/* 상세 이용 안내 팝업 */}
{showHelp && (
  <div
    className="modal-backdrop"
    onMouseDown={(e) => {
      if (e.target === e.currentTarget) {
        setShowHelp(false);
      }
    }}
  >
    <div
      className="guide-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="guide-title"
    >
      <div className="guide-header">
        <div>
          <span className="eyebrow">ONESLOT USER GUIDE</span>
          <h2 id="guide-title">OneSlot 이용 가이드</h2>
          <p>
            공간 검색부터 예약, 이용 현황 확인,
            예약 취소까지 안내해 드립니다.
          </p>
        </div>

        <button
          className="modal-close"
          aria-label="도움말 닫기"
          onClick={() => setShowHelp(false)}
        >
          ✕
        </button>
      </div>

      <div className="guide-body">
        <section className="guide-section">
          <h3>01. 공간 예약하기</h3>
          <ol>
            <li>
              왼쪽 메뉴에서 <strong>공간 예약</strong>을
              선택합니다.
            </li>
            <li>
              예약하려는 날짜를 선택합니다.
              날짜 좌우의 화살표로 다른 날짜를
              확인할 수도 있습니다.
            </li>
            <li>
              공간별 예약 현황에서 원하는 공간의
              <strong> 예약</strong> 버튼을 누르거나
              빈 시간대를 클릭합니다.
            </li>
            <li>
              예약 팝업에서 공간, 날짜,
              시작 시간, 종료 시간, 사용 목적을
              입력합니다.
            </li>
            <li>
              선택한 시간이 예약 가능하면
              <strong> 예약 확정하기</strong>를
              클릭합니다.
            </li>
            <li>
              예약이 완료되면
              <strong> 나의 예약</strong> 메뉴에서
              확인할 수 있습니다.
            </li>
          </ol>
        </section>

        <section className="guide-section">
          <h3>02. 예약 시간 선택 기준</h3>

          <div className="guide-rule-grid">
            <div>
              <span>운영 시간</span>
              <strong>09:00 ~ 21:00</strong>
            </div>

            <div>
              <span>예약 시간 단위</span>
              <strong>10분 간격</strong>
            </div>

            <div>
              <span>최소 예약 시간</span>
              <strong>10분</strong>
            </div>

            <div>
              <span>예약 가능 조건</span>
              <strong>중복되지 않는 시간</strong>
            </div>
          </div>

          <p>
            시작 시간과 종료 시간을 각각 직접
            선택할 수 있습니다. 반드시 1시간 단위로
            예약할 필요는 없습니다.
          </p>

          <div className="guide-example">
            <strong>예약 예시</strong>
            <div className="guide-time-bars">
              <div className="guide-time-booked">
                09:00 ~ 10:10
                <small>기존 예약</small>
              </div>
              <div className="guide-time-free">
                10:10 ~ 11:00
                <small>새 예약 가능</small>
              </div>
            </div>
            <p>
              첫 번째 사용자가 09:00부터
              10:10까지 이용한다면, 다른 사용자는
              10:10부터 바로 예약할 수 있습니다.
            </p>
          </div>
        </section>

        <section className="guide-section">
          <h3>03. 예약 현황 시간표 보는 방법</h3>

          <div className="guide-legend">
            <span>
              <i className="guide-dot reserved" />
              다른 사용자가 예약한 시간
            </span>
            <span>
              <i className="guide-dot mine" />
              내가 예약한 시간
            </span>
            <span>
              <i className="guide-dot available" />
              예약 가능한 시간
            </span>
          </div>

          <p>
            같은 공간에서 기존 예약과 시간이
            겹치면 예약할 수 없습니다.
            예약 종료 시각과 새 예약 시작 시각이
            같은 것은 허용됩니다.
          </p>

          <p>
            오늘 날짜에 이미 지난 시간은
            예약할 수 없습니다.
          </p>
        </section>

        <section className="guide-section">
          <h3>04. 예약 내역 조회 및 취소</h3>

          <ol>
            <li>
              왼쪽 메뉴에서
              <strong> 나의 예약</strong>을 클릭합니다.
            </li>
            <li>
              예약한 공간, 날짜, 시작·종료 시간,
              이용 시간을 확인합니다. 시간 변경은 <strong>예약 변경</strong> 버튼에서 할 수 있습니다.
            </li>
            <li>
              취소할 예약의
              <strong> 예약 취소</strong> 버튼을
              누릅니다.
            </li>
            <li>
              확인 창에서
              <strong> 예약 취소</strong>를
              다시 클릭합니다.
            </li>
            <li>
              취소한 예약은 취소 내역에 표시되며
              해당 시간은 다시 예약 가능 상태가 됩니다.
            </li>
          </ol>

          <div className="guide-notice">
            실제 서비스의 결제 취소·환불 정책은 팀 결제 기능과 통합한 후 적용됩니다.
            현재는 개발용 사용자 ID 1로 예약 API를 테스트합니다.
          </div>
        </section>

        <section className="guide-section">
          <h3>05. 메뉴별 기능 안내</h3>

          <div className="guide-menu-table">
            <div>
              <strong>대시보드</strong>
              <span>
                전체 공간 수, 선택 날짜의 예약 현황,
                나의 예약 건수를 확인합니다.
              </span>
            </div>
            <div>
              <strong>공간 예약</strong>
              <span>
                날짜별 시간표를 확인하고
                원하는 시간을 예약합니다.
              </span>
            </div>
            <div>
              <strong>공간 둘러보기</strong>
              <span>
                공간별 위치, 수용 인원, 시설을
                확인하고 예약 화면으로 이동합니다.
              </span>
            </div>
            <div>
              <strong>나의 예약</strong>
              <span>
                현재 예약 내역을 확인하고
                예약을 변경하거나 취소합니다.
              </span>
            </div>
            <div>
              <strong>이용 현황</strong>
              <span>
                선택 날짜의 공간별 예약 시간과
                이용률을 확인합니다.
              </span>
            </div>
            <div>
              <strong>설정</strong>
              <span>
                예약 운영 기준을 확인하고
                데모 데이터를 초기화합니다.
              </span>
            </div>
          </div>
        </section>

        <section className="guide-section">
          <h3>06. 꼭 알아두세요</h3>

          <div className="guide-notice">
            <strong>예약 API 연동 테스트 버전입니다.</strong>
            <p>예약 생성·조회·취소는 Spring Boot 및 MariaDB를 사용합니다.</p>
            <p>로그인은 아직 연결되지 않아 개발용 사용자 ID 1을 사용합니다.
            결제·환불 기능과 관리자는 별도 연동이 필요합니다.</p>
          </div>
        </section>
      </div>

      <div className="guide-footer">
        <span>OneSlot · 사용자 이용 안내</span>
        <button
          className="primary-button"
          onClick={() => setShowHelp(false)}
        >
          확인했습니다
        </button>
      </div>
    </div>
  </div>
)}

      {/* 예약 팝업 */}
      {modalOpen && (
        <div
          className="modal-backdrop"
          onMouseDown={(e) => {
            if (
              e.target === e.currentTarget
            ) {
              setModalOpen(false);
            }
          }}
        >
          <div className="reservation-modal">
            <div className="modal-heading">
              <div>
                <span className="eyebrow">
                  NEW BOOKING
                </span>
                <h2>{editingBooking ? "예약 시간 변경" : "공간 예약하기"}</h2>
                <p>
                  시작·종료 시간을
                  10분 단위로 선택하세요.
                </p>
              </div>

              <button
                className="modal-close"
                onClick={() =>
                  setModalOpen(false)
                }
              >
                ✕
              </button>
            </div>

            <div className="modal-body">
              <label className="form-field">
                <span>예약 공간</span>
                <select
                  value={roomId}
                  onChange={(e) =>
                    setRoomId(
                      Number(e.target.value)
                    )
                  }
                >
                  {ROOMS.map((r) => (
                    <option
                      value={r.id}
                      key={r.id}
                      disabled={!CONNECTED_SPACE_IDS.includes(r.id) || (editingBooking && r.id !== editingBooking.roomId)}
                    >
                      {r.name} · {r.capacity}명
                      {!CONNECTED_SPACE_IDS.includes(r.id) ? " (준비 중)" : ""}
                    </option>
                  ))}
                </select>
              </label>

              <label className="form-field">
                <span>이용 날짜</span>
                <input
                  type="date"
                  min={toDate(new Date())}
                  value={day}
                  onChange={(e) =>
                    setDay(e.target.value)
                  }
                />
              </label>

              <div className="time-input-grid">
                <label className="form-field">
                  <span>시작 시간</span>

                  <select
                    value={start}
                    onChange={(e) => {
                      const nextStart =
                        Number(
                          e.target.value
                        );

                      setStart(nextStart);

                      if (
                        end <= nextStart
                      ) {
                        setEnd(
                          nextStart + STEP
                        );
                      }
                    }}
                  >
                    {OPTIONS.slice(0, -1).map(
                      (minute) => (
                        <option
                          value={minute}
                          key={minute}
                        >
                          {toTime(minute)}
                        </option>
                      )
                    )}
                  </select>
                </label>

                <label className="form-field">
                  <span>종료 시간</span>

                  <select
                    value={end}
                    onChange={(e) =>
                      setEnd(
                        Number(
                          e.target.value
                        )
                      )
                    }
                  >
                    {OPTIONS.filter(
                      (minute) =>
                        minute > start
                    ).map((minute) => (
                      <option
                        key={minute}
                        value={minute}
                      >
                        {toTime(minute)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <div className="duration-panel">
                <span>◷</span>
                <div>
                  <strong>
                    {toTime(start)} ~{" "}
                    {toTime(end)}
                  </strong>
                  <small>
                    총{" "}
                    {durationText(
                      start,
                      end
                    )}{" "}
                    이용
                  </small>
                </div>
              </div>

              {conflict && (
                <div className="warning-box">
                  이미{" "}
                  {toTime(conflict.start)}
                  ~
                  {toTime(conflict.end)}
                  에 예약이 있습니다.
                  다른 시간을 선택해 주세요.
                </div>
              )}

              {!isFuture(day, start) && (
                <div className="warning-box">
                  지난 시간에는 예약할 수
                  없습니다.
                </div>
              )}

              <label className="form-field">
                <span>사용 목적</span>
                <input
                  value={purpose}
                  onChange={(e) =>
                    setPurpose(
                      e.target.value
                    )
                  }
                  placeholder="회의 목적을 입력하세요"
                  maxLength={60}
                />
              </label>

              <div className="booking-hint">
                ✓ 앞 예약이 10:10에 끝나면
                다음 예약은 10:10부터
                가능합니다.
              </div>
            </div>

            <div className="modal-actions">
              <button
                className="secondary-button"
                onClick={() =>
                  setModalOpen(false)
                }
              >
                닫기
              </button>

              <button
                className="primary-button"
                disabled={!canReserve}
                onClick={
                  createReservation
                }
              >
                  {saving ? "저장 중..." : canReserve
                    ? (editingBooking ? "변경 저장하기" : "예약 요청하기")
                  : "예약 불가능"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 취소 확인 팝업 */}
      {cancelTarget && (
        <div className="modal-backdrop">
          <div className="cancel-modal">
            <div className="cancel-symbol">
              ×
            </div>

            <h2>예약을 취소할까요?</h2>

            <p>
              {
                ROOMS.find(
                  (r) =>
                    r.id ===
                    cancelTarget.roomId
                )?.name
              }
            </p>

            <strong>
              {cancelTarget.day}
              <br />
              {toTime(
                cancelTarget.start
              )}
              {" ~ "}
              {toTime(
                cancelTarget.end
              )}
            </strong>

            <small>
              취소하면 해당 시간을
              다른 사용자가 예약할 수 있습니다.
            </small>

            <div className="cancel-actions">
              <button
                className="secondary-button"
                onClick={() =>
                  setCancelTarget(null)
                }
              >
                유지하기
              </button>

              <button
                className="danger-button"
                onClick={
                  cancelReservation
                }
              >
                {saving ? "처리 중..." : "예약 취소"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 알림 */}
      {notice && (
        <div
          className="toast-message"
          role="status"
        >
          <span>✓</span>
          {notice}
          <button
            onClick={() =>
              setNotice("")
            }
          >
            ✕
          </button>
        </div>
      )}
    </div>
  );
}
