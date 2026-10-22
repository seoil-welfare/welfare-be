-- =====================================================================
-- 개발용 가상 일지 (local 프로필에서만 들어감) — 통계 화면 시연·테스트용
--
-- - 2026-04-01 ~ 2026-10-15 평일, 활성 이용자마다 하루 1장 (가끔 결석)
-- - 하루 활동 2개 (10:00~11:30, 13:30~15:00), 일부 활동에 도전적 행동
-- - 값은 날짜·이용자로 만든 숫자(hashtext)로 정해서, 몇 번을 만들어도 같은 데이터가 나온다
-- - 이미 일지가 있는 날짜는 건너뛴다 (ON CONFLICT DO NOTHING) → 직접 쓴 일지를 덮어쓰지 않음
-- - R__ 로 시작하는 파일은 '반복 실행' 파일: 내용을 고치면 다음 실행 때 다시 돈다
-- =====================================================================
DO $$
DECLARE
    c        RECORD;         -- 이용자 한 명
    d        DATE;           -- 날짜
    h        INT;            -- 이 이용자·날짜로 만든 숫자 (값 고르기에 씀)
    slot     INT;            -- 1 = 오전 활동, 2 = 오후 활동
    k        INT;            -- 슬롯마다 다른 값을 고르기 위한 숫자
    lid      BIGINT;         -- 새로 만든 일지 번호
    rid      BIGINT;         -- 새로 만든 활동 번호
    st       TEXT;           -- draft / completed
    ts       TIMESTAMPTZ;    -- 저장 시각 (그날 17시로 가정)
    cond     SMALLINT;       -- 컨디션 번호
    area     TEXT;           -- 도전적 행동 영역 코드
BEGIN
    FOR c IN SELECT client_id, staff_id FROM client WHERE status = 'active' ORDER BY client_id LOOP
        FOR d IN SELECT g::date FROM generate_series(DATE '2026-04-01', DATE '2026-10-15', INTERVAL '1 day') g
                 WHERE extract(isodow FROM g) < 6 LOOP                          -- 월~금만
            h := abs(hashtext(c.client_id || '-' || d));
            CONTINUE WHEN h % 10 = 0;                                           -- 열흘에 하루 꼴 결석

            st := CASE WHEN d >= DATE '2026-10-14' AND h % 3 = 0 THEN 'draft' ELSE 'completed' END;
            ts := (d + TIME '17:00') AT TIME ZONE 'Asia/Seoul';
            SELECT condition_id INTO cond FROM condition_status WHERE sort_order = 1 + (h / 7) % 5;

            INSERT INTO daily_log (client_id, log_date, status, weather_id, condition_id, condition_etc,
                                   remark, created_by, created_at, updated_at)
            VALUES (c.client_id, d, st,
                    (SELECT weather_id FROM weather WHERE sort_order = 1 + h % 5),
                    cond,
                    CASE WHEN (SELECT is_etc FROM condition_status WHERE condition_id = cond) THEN '오전에 졸려함' END,
                    '[가상 데이터]', c.staff_id, ts, ts)
            ON CONFLICT (client_id, log_date) DO NOTHING
            RETURNING log_id INTO lid;
            CONTINUE WHEN lid IS NULL;                                          -- 이미 있던 날짜면 건너뜀

            FOR slot IN 1..2 LOOP
                k := h / (slot * 3 + 1);
                INSERT INTO activity_record (log_id, start_time, end_time, content, has_behavior)
                VALUES (lid,
                        CASE slot WHEN 1 THEN TIME '10:00' ELSE TIME '13:30' END,
                        CASE slot WHEN 1 THEN TIME '11:30' ELSE TIME '15:00' END,
                        CASE slot WHEN 1 THEN '오전 활동 (가상)' ELSE '오후 활동 (가상)' END,
                        k % 4 = 0)                                              -- 네 번에 한 번 꼴 도전적 행동
                RETURNING record_id INTO rid;

                -- 참여 활동 1개 (+ 가끔 '기타')
                INSERT INTO record_activity_type (record_id, activity_type_id, etc_text)
                SELECT rid, activity_type_id, NULL FROM activity_type WHERE sort_order = 1 + k % 7;
                IF k % 13 = 0 THEN
                    INSERT INTO record_activity_type (record_id, activity_type_id, etc_text)
                    SELECT rid, activity_type_id, '공원 산책' FROM activity_type WHERE is_etc;
                END IF;

                -- 사전 지원: 가끔 '없음' 하나만, 아니면 1~2개
                IF k % 11 = 0 THEN
                    INSERT INTO record_pre_support (record_id, pre_support_id, etc_text)
                    SELECT rid, pre_support_id, NULL FROM pre_support WHERE code = 'none';
                ELSE
                    INSERT INTO record_pre_support (record_id, pre_support_id, etc_text)
                    SELECT rid, pre_support_id, NULL FROM pre_support
                    WHERE sort_order IN (1 + k % 7, 1 + (k / 5) % 7);
                END IF;

                -- 이용자 반응 1개, 긍정행동지원 1개
                INSERT INTO record_reaction (record_id, reaction_id, etc_text)
                SELECT rid, reaction_id, NULL FROM reaction WHERE sort_order = 1 + (k / 2) % 4;
                INSERT INTO record_pbs_support (record_id, pbs_support_id, etc_text)
                SELECT rid, pbs_support_id, NULL FROM pbs_support WHERE sort_order = 1 + (k / 3) % 5;

                -- 도전적 행동: 영역 1개에서 세부 항목 1~2개 (+ 가끔 상위 '기타')
                IF k % 4 = 0 THEN
                    area := (ARRAY['otherHarm', 'selfHarm', 'destruction'])[1 + (k / 4) % 3];
                    INSERT INTO record_behavior (record_id, item_id, etc_text)
                    SELECT rid, i.item_id, NULL
                    FROM behavior_item i JOIN behavior_area a ON a.area_id = i.area_id
                    WHERE a.code = area AND NOT i.is_etc
                      AND i.sort_order IN (1 + (k / 8) % 5, 1 + (k / 40) % 5);
                    IF k % 9 = 0 THEN
                        INSERT INTO record_behavior (record_id, item_id, etc_text)
                        SELECT rid, item_id, '큰 소리로 울기' FROM behavior_item WHERE code = 'other';
                    END IF;
                END IF;
            END LOOP;

            -- 작성 완료 일지는 '등록' 소식 이력
            IF st = 'completed' THEN
                INSERT INTO log_history (log_id, kind, actor_id, occurred_at) VALUES (lid, 'created', c.staff_id, ts);
            END IF;
        END LOOP;
    END LOOP;
END $$;
