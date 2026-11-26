package com.reservation.space.service;

import com.reservation.reservation.entity.ReservationStatus;
import com.reservation.space.dto.*;
import com.reservation.space.entity.OperatingWindow;
import com.reservation.space.entity.Space;
import com.reservation.space.entity.SpaceImage;
import com.reservation.space.entity.SpaceOperatingHour;
import com.reservation.space.repository.SpaceImageRepository;
import com.reservation.space.repository.SpaceOperatingHourRepository;
import com.reservation.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceService {

    private static final List<ReservationStatus> BLOCKING = List.of(ReservationStatus.CONFIRMED);
    private static final List<ReservationStatus> POPULAR_COUNTED = List.of(ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED);

    private final SpaceRepository spaceRepository;
    private final SpaceImageRepository imageRepository;
    private final SpaceOperatingHourRepository hourRepository;

    // =====================================================
    // 사용자 화면
    // =====================================================

    /**
     * 공간 검색 (RSV_UI_005).
     * 시간 조건이 있으면 그 시간에 운영하고, 예약이 겹치지 않는 활성 공간만.
     * 날짜만 있으면 그날 휴무가 아닌 공간만.
     */
    public PageResponse<SpaceSummaryResponse> search(LocalDate date, LocalTime start, LocalTime end,
                                                     Integer people, String location, String sort,
                                                     int page, int size) {
        if ((start == null) != (end == null)) {
            throw badRequest("시작 시간과 종료 시간을 함께 입력하세요.");
        }
        if (start != null) {
            validateTenMinuteRange(start, end);
            if (date == null) {
                date = LocalDate.now();
            }
        }

        List<Space> spaces = spaceRepository.findByActiveTrue().stream()
                .filter(s -> people == null || (s.getCapacity() != null && s.getCapacity() >= people))
                .filter(s -> !StringUtils.hasText(location)
                        || (s.getLocation() != null && s.getLocation().contains(location.trim())))
                .collect(Collectors.toCollection(ArrayList::new));

        if (date != null && !spaces.isEmpty()) {
            Map<Long, OperatingWindow> windows = windowsOf(idsOf(spaces), date);
            if (start != null) {
                LocalDateTime startAt = date.atTime(start);
                LocalDateTime endAt = date.atTime(end);
                Set<Long> busy = new HashSet<>(spaceRepository.findBusySpaceIds(
                        startAt, endAt, BLOCKING, ReservationStatus.PENDING_PAYMENT, LocalDateTime.now()));
                spaces.removeIf(s -> busy.contains(s.getId())
                        || !windows.get(s.getId()).covers(startAt, endAt));
            } else {
                spaces.removeIf(s -> windows.get(s.getId()).closed());
            }
        }

        spaces.sort(comparatorOf(sort));
        PageResponse<Space> sliced = PageResponse.slice(spaces, page, size);
        return new PageResponse<>(toSummaries(sliced.content()), sliced.page(), sliced.size(),
                sliced.totalElements(), sliced.totalPages());
    }

    /** 메인 인기 공간 (RSV_UI_002): 최근 30일 확정·이용완료 예약이 많은 순, 모자라면 최신 공간으로 채움 */
    public List<SpaceSummaryResponse> popular(int limit) {
        List<Long> ids = spaceRepository.findPopularSpaceIds(
                LocalDateTime.now().minusDays(30), POPULAR_COUNTED, PageRequest.of(0, limit * 2));
        Map<Long, Space> active = spaceRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(Space::getId, Function.identity()));

        LinkedHashMap<Long, Space> picked = new LinkedHashMap<>();
        ids.stream().map(active::get).filter(Objects::nonNull).limit(limit)
                .forEach(s -> picked.put(s.getId(), s));
        active.values().stream().sorted(Comparator.comparing(Space::getId).reversed())
                .filter(s -> !picked.containsKey(s.getId()))
                .limit(Math.max(0, limit - picked.size()))
                .forEach(s -> picked.put(s.getId(), s));
        return toSummaries(new ArrayList<>(picked.values()));
    }

    /** 공간 상세 (RSV_UI_006). 비활성 공간은 사용자에게 없는 공간 */
    public SpaceDetailResponse getActiveDetail(Long spaceId) {
        Space space = findSpace(spaceId);
        if (!Boolean.TRUE.equals(space.getActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공간을 찾을 수 없습니다.");
        }
        return toDetail(space);
    }

    /** 특정 날짜 운영 시간 (예약 캘린더용) */
    public DayHoursResponse getDayHours(Long spaceId, LocalDate date) {
        findSpace(spaceId);
        OperatingWindow w = getOperatingWindow(spaceId, date);
        return new DayHoursResponse(date, w.open(), w.close(), w.closed());
    }

    // =====================================================
    // 예약 파트(임호현)에서 쓰는 메서드
    // =====================================================

    /** 그날의 운영 시간. 운영 시간을 등록하지 않은 공간은 09:00~21:00 */
    public OperatingWindow getOperatingWindow(Long spaceId, LocalDate date) {
        return hourRepository.findBySpaceIdAndDayOfWeek(spaceId, date.getDayOfWeek().getValue())
                .map(SpaceOperatingHour::toWindow)
                .orElse(OperatingWindow.DEFAULT);
    }

    /** 예약 시간이 운영 시간 안인지 확인. 아니면 400 */
    public void validateWithinOperatingHours(Long spaceId, LocalDateTime startAt, LocalDateTime endAt) {
        OperatingWindow w = getOperatingWindow(spaceId, startAt.toLocalDate());
        if (w.closed()) {
            throw badRequest("휴무일에는 예약할 수 없습니다.");
        }
        if (!w.covers(startAt, endAt)) {
            throw badRequest("예약 가능 시간은 " + w.open() + "~" + w.close() + "입니다.");
        }
    }

    // =====================================================
    // 관리자 (RSV_UI_009, 009-1)
    // =====================================================

    /** 관리자 목록: 비활성 포함, 최근 등록순 */
    public PageResponse<SpaceSummaryResponse> adminList(String keyword, Boolean active, int page, int size) {
        List<Space> spaces = spaceRepository.findAll().stream()
                .filter(s -> active == null || active.equals(s.getActive()))
                .filter(s -> !StringUtils.hasText(keyword)
                        || contains(s.getName(), keyword) || contains(s.getLocation(), keyword))
                .sorted(Comparator.comparing(Space::getId).reversed())
                .toList();
        PageResponse<Space> sliced = PageResponse.slice(spaces, page, size);
        return new PageResponse<>(toSummaries(sliced.content()), sliced.page(), sliced.size(),
                sliced.totalElements(), sliced.totalPages());
    }

    public SpaceDetailResponse adminDetail(Long spaceId) {
        return toDetail(findSpace(spaceId));
    }

    @Transactional
    public SpaceDetailResponse create(SpaceRequest request) {
        Space space = new Space();
        apply(space, request);
        space.setActive(true);
        spaceRepository.save(space);
        saveHours(space, request.operatingHours());
        return toDetail(space);
    }

    @Transactional
    public SpaceDetailResponse update(Long spaceId, SpaceRequest request) {
        Space space = findSpace(spaceId);
        apply(space, request);
        if (request.operatingHours() != null && !request.operatingHours().isEmpty()) {
            saveHours(space, request.operatingHours());
        }
        return toDetail(space);
    }

    /** 공간은 지우지 않고 비활성으로 숨긴다 (예약 이력 보존). 비활성 공간은 검색·예약 불가 */
    @Transactional
    public SpaceSummaryResponse changeActive(Long spaceId, boolean active) {
        Space space = findSpace(spaceId);
        space.setActive(active);
        return toSummaries(List.of(space)).get(0);
    }

    // =====================================================
    // 내부
    // =====================================================

    // public 이어야 함: @Transactional 프록시를 거쳐 다른 서비스에서 호출
    public Space findSpace(Long spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공간을 찾을 수 없습니다."));
    }

    private void apply(Space space, SpaceRequest r) {
        space.setName(r.name().trim());
        space.setLocation(r.location().trim());
        space.setCapacity(r.capacity());
        space.setPricePerHour(r.pricePerHour());
        space.setDescription(StringUtils.hasText(r.description()) ? r.description().trim() : null);
    }

    /** 요일별 운영 시간 저장. 기존 줄은 고쳐 쓰고 없는 요일만 새로 만든다 (유니크 충돌 방지) */
    private void saveHours(Space space, List<OperatingHourDto> hours) {
        List<OperatingHourDto> input = (hours == null || hours.isEmpty()) ? defaultHours() : hours;
        validateHours(input);

        Map<Integer, SpaceOperatingHour> existing = space.getId() == null ? Map.of()
                : hourRepository.findBySpaceIdOrderByDayOfWeekAsc(space.getId()).stream()
                .collect(Collectors.toMap(SpaceOperatingHour::getDayOfWeek, Function.identity()));

        for (OperatingHourDto dto : input) {
            SpaceOperatingHour row = existing.get(dto.dayOfWeek());
            if (row == null) {
                row = SpaceOperatingHour.create(space, dto.dayOfWeek());
            }
            row.change(dto.openTime(), dto.closeTime(), dto.closed());
            hourRepository.save(row);
        }
    }

    private static List<OperatingHourDto> defaultHours() {
        List<OperatingHourDto> list = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            list.add(new OperatingHourDto(d, OperatingWindow.DEFAULT.open(), OperatingWindow.DEFAULT.close(), false));
        }
        return list;
    }

    private void validateHours(List<OperatingHourDto> hours) {
        Set<Integer> days = new HashSet<>();
        for (OperatingHourDto h : hours) {
            if (!days.add(h.dayOfWeek())) {
                throw badRequest("같은 요일이 두 번 들어 있습니다.");
            }
            if (!h.closed()) {
                if (h.openTime() == null || h.closeTime() == null) {
                    throw badRequest("운영 요일은 시작·종료 시각을 입력하세요.");
                }
                validateTenMinuteRange(h.openTime(), h.closeTime());
            }
        }
        if (days.size() != 7) {
            throw badRequest("월요일부터 일요일까지 7개 요일을 모두 입력하세요.");
        }
    }

    private static void validateTenMinuteRange(LocalTime start, LocalTime end) {
        if (start.getMinute() % 10 != 0 || end.getMinute() % 10 != 0
                || start.getSecond() != 0 || end.getSecond() != 0) {
            throw badRequest("시간은 10분 단위로 입력하세요.");
        }
        if (!start.isBefore(end)) {
            throw badRequest("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }

    private Map<Long, OperatingWindow> windowsOf(List<Long> spaceIds, LocalDate date) {
        int day = date.getDayOfWeek().getValue();
        Map<Long, OperatingWindow> map = new HashMap<>();
        spaceIds.forEach(id -> map.put(id, OperatingWindow.DEFAULT));
        hourRepository.findBySpaceIdIn(spaceIds).stream()
                .filter(h -> h.getDayOfWeek() == day)
                .forEach(h -> map.put(h.getSpace().getId(), h.toWindow()));
        return map;
    }

    private List<SpaceSummaryResponse> toSummaries(List<Space> spaces) {
        if (spaces.isEmpty()) {
            return List.of();
        }
        Map<Long, String> thumbs = new HashMap<>();
        for (SpaceImage img : imageRepository.findBySpaceIdInOrderBySortOrderAsc(idsOf(spaces))) {
            thumbs.putIfAbsent(img.getSpace().getId(), ImageResponse.from(img).url());
        }
        return spaces.stream().map(s -> SpaceSummaryResponse.of(s, thumbs.get(s.getId()))).toList();
    }

    private SpaceDetailResponse toDetail(Space space) {
        List<ImageResponse> images = imageRepository.findBySpaceIdOrderBySortOrderAsc(space.getId())
                .stream().map(ImageResponse::from).toList();
        List<OperatingHourDto> hours = hourRepository.findBySpaceIdOrderByDayOfWeekAsc(space.getId())
                .stream().map(OperatingHourDto::from).toList();
        if (hours.isEmpty()) {
            hours = defaultHours();
        }
        return SpaceDetailResponse.of(space, images, hours);
    }

    private static Comparator<Space> comparatorOf(String sort) {
        Comparator<Space> latest = Comparator.comparing(Space::getId).reversed();
        if (sort == null) {
            return latest;
        }
        return switch (sort) {
            case "price_asc" -> Comparator.comparing(Space::getPricePerHour, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(latest);
            case "price_desc" -> Comparator.comparing(Space::getPricePerHour, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(latest);
            case "capacity" -> Comparator.comparing(Space::getCapacity, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(latest);
            default -> latest;
        };
    }

    private static List<Long> idsOf(List<Space> spaces) {
        return spaces.stream().map(Space::getId).toList();
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword.trim());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
