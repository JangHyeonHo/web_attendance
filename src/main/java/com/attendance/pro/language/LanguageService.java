package com.attendance.pro.language;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.attendance.pro.language.LanguageDtos.EntryResponse;
import com.attendance.pro.language.LanguageDtos.UpsertRequest;

/**
 * 언어 마스터(다국어 텍스트) 서비스.
 */
@Service
public class LanguageService {

    private final LanguageMapper languageMapper;

    public LanguageService(LanguageMapper languageMapper) {
        this.languageMapper = languageMapper;
    }

    /**
     * 화면 단위 텍스트 맵(key -> value) 취득. 프론트 화면 렌더링용.
     */
    public Map<String, String> texts(String windowId, String lang) {
        Map<String, String> texts = new LinkedHashMap<>();
        for (LanguageEntry entry : languageMapper.find(windowId, lang)) {
            texts.put(entry.langKey(), entry.langValue());
        }
        return texts;
    }

    /**
     * 관리자용 전체 목록 조회(조건 없으면 전체).
     */
    public List<EntryResponse> list(String windowId, String lang) {
        return languageMapper.find(windowId, lang).stream()
                .map(EntryResponse::from)
                .toList();
    }

    /**
     * 관리자용 등록/갱신.
     */
    public void upsert(UpsertRequest request) {
        languageMapper.upsert(request.windowId(), request.langKey(), request.lang(), request.langValue());
    }

}
