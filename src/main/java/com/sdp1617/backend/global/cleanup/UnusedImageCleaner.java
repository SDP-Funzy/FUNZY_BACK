package com.sdp1617.backend.global.cleanup;

import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.s3.S3ImageService;
import com.sdp1617.backend.letter.repository.LetterCardRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 어디에서도 쓰지 않는 S3 사진을 지운다 (#122).
 * 카드 사진은 카드를 지우거나 사진을 바꿔도 바로 지우지 않는다 — "다른 데서 안 쓰임"을 확인하고 지우는 사이에
 * 다른 요청이 같은 사진을 카드에 넣으면 사진이 깨지기 때문(#82). 대신 여기서 올린 지 충분히 지난 사진만 모아 지운다.
 * 사진을 쓰는 곳: 편지 카드(letter_cards.image_key), 프로필(members.profile_image_key),
 * 콕한 아카이브 카드(archive_cards.image_url — 편지 카드 사진 주소를 복사해 같은 파일을 씀).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnusedImageCleaner {

    /** 업로드 경로 (LetterCardBoxService·ProfileService의 presigned URL 발급 prefix). */
    static final List<String> IMAGE_PREFIXES = List.of("cards/", "profiles/");

    /** 주소에서 S3 key를 뽑는다. 이미지 기본 주소(버킷·CDN) 설정이 바뀌어도 key 부분은 같다. */
    private static final Pattern KEY_IN_URL = Pattern.compile("(?:^|/)((?:cards|profiles)/[^?#]+)");

    private final S3ImageService s3ImageService;
    private final LetterCardRepository letterCardRepository;
    private final MemberRepository memberRepository;
    private final ArchiveCardRepository archiveCardRepository;

    /** uploadedBefore보다 먼저 올라갔고 어디에서도 쓰지 않는 사진을 지우고, 지운 개수를 돌려준다. */
    public int deleteUnusedImagesUploadedBefore(Instant uploadedBefore) {
        Set<String> archivedKeys = archivedImageKeys();
        // 안전장치: 사진을 쓰는 곳이 DB에 하나도 없으면 지우지 않는다. 운영 버킷을 바라보는 빈 DB(로컬 등)에서
        // 실수로 켜졌을 때 운영 사진이 모두 "안 쓰는 사진"으로 보여 지워지는 것을 막는다.
        if (archivedKeys.isEmpty() && !letterCardRepository.existsByImageKeyIsNotNull()
                && !memberRepository.existsByProfileImageKeyIsNotNull()) {
            log.error("사진을 쓰는 카드·프로필·아카이브가 DB에 하나도 없어 사진 정리를 건너뜁니다. 연결된 DB와 S3 버킷을 확인하세요.");
            return 0;
        }
        int deleted = 0;
        for (String prefix : IMAGE_PREFIXES) {
            int[] deletedInPrefix = {0};
            s3ImageService.forEachPageOfImagesUploadedBefore(prefix, uploadedBefore, keys -> {
                Set<String> inUse = new HashSet<>(archivedKeys);
                inUse.addAll(letterCardRepository.findImageKeysIn(keys));
                inUse.addAll(memberRepository.findProfileImageKeysIn(keys));
                List<String> unused = keys.stream().filter(key -> !inUse.contains(key)).toList();
                deletedInPrefix[0] += s3ImageService.deleteImages(unused);
            });
            deleted += deletedInPrefix[0];
        }
        return deleted;
    }

    private Set<String> archivedImageKeys() {
        Set<String> keys = new HashSet<>();
        for (String url : archiveCardRepository.findAllImageUrls()) {
            Matcher matcher = KEY_IN_URL.matcher(url);
            if (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }
}
