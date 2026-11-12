package kr.seoil.welfare.card.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.seoil.welfare.card.CardRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Responses API로 카드 문장을 만든다.
 *
 *  - 구조화 출력(json_schema, strict): 그림·표정은 스키마의 enum 안에서만 고르게 한다
 *  - store=false: 요청·응답을 OpenAI 쪽에 저장하지 않는다 (API 입력은 원래 학습에 쓰지 않음)
 *  - 30초 안에 응답이 없으면 한 번 다시 시도, 그래도 실패면 예외 → 카드 failed
 */
public class OpenAiCardGenerator implements CardGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCardGenerator.class);

    static final String INSTRUCTIONS = """
            너는 최중증 발달장애인 주간활동 기관의 '오늘 카드'를 쓰는 도우미다.
            입력은 담당 선생님이 쓴 하루 일지(JSON)다. 활동 하나마다 카드 한 장을 만든다.

            [카드 앞면 — 이용자에게 읽어 주는 면]
            - word: 활동에서 한 것을 나타내는 자연스러운 낱말 하나 (예: 북, 그림, 산책, 샌드위치). 10자 이내.
              낱말을 잘라 줄이지 않는다 (샌드위치 O, 샌드 X).
            - easyText: 아주 쉬운 말로 1~2문장, 40자 안팎. 예: "북을 쳤어요. 신났어요."
            - mood: excited(아주 즐거움) / good(좋음) / tired(피곤함) 중 하나. 이용자 반응에 맞춘다.
            - 앞면에는 좋았던 순간만 쓴다. 도전적 행동(타해·자해·파괴행동 등)이나 그 세부 내용은 앞면에 절대 쓰지 않는다.

            [카드 뒷면 — 보호자 알림장]
            - guardianText: 그 활동에서 있었던 일을 사실대로 존댓말(해요체)로 2~4문장.
            - 일지에 적힌 사실만 쓴다. 지원 항목(사전 지원, 긍정행동지원)은 선생님이 한 일로만 적고,
              그 지원 '덕분에' 좋아졌다는 식의 효과나 결과는 활동 상세 내용에 그렇게 적혀 있을 때만 쓴다.
            - 도전적 행동이 있었으면 사실만 부드럽게 적는다. 진단·평가·원인 추측·행동 예측·지원 방법 추천은 하지 않는다.
            - homeTip: 활동 상세 내용에 '무엇을 해 주니 나아졌다'처럼 도움이 된 일이 직접 적혀 있을 때만,
              그 일을 집에서도 해 볼 수 있게 한 문장으로 쓴다. 그런 내용이 없으면 반드시 null.

            [공통]
            - 이름은 입력의 nickname만 쓴다. 다른 이름이나 호칭을 만들지 않는다.
            - pictogram은 활동에 맞는 그림 하나: music, meal, art, sports, cooking, walk, outing, daily, favorite, rest
            - items는 입력 activities와 같은 순서, 같은 개수로 만들고 time은 입력 값을 그대로 쓴다.
            - 하루 특이사항(notes)과 컨디션은 각 활동 카드에 넣지 않고 summary에만 쓴다.
            - summary: 하루 전체를 보호자에게 전하는 한두 문장 (오늘의 한마디).
            """;

    private final RestClient client;
    private final ObjectMapper json;
    private final String model;
    private final String reasoningEffort;

    public OpenAiCardGenerator(String apiKey, String model, String reasoningEffort, Duration timeout, ObjectMapper json) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.json = json;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
    }

    @Override
    public String name() {
        return "openai:" + model;
    }

    @Override
    public GeneratedCard generate(CardSource source) {
        Map<String, Object> body = requestBody(source);
        JsonNode response;
        try {
            response = call(body);
        } catch (ResourceAccessException timeout) {                 // 시간 초과·연결 실패는 한 번만 다시
            log.warn("오늘 카드 AI 응답 지연, 다시 시도: {}", timeout.getMessage());
            response = call(body);
        }
        return parse(response);
    }

    private JsonNode call(Map<String, Object> body) {
        return client.post().uri("/responses").contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class);
    }

    /** 요청 본문 (테스트에서 개인정보가 없는지 확인한다) */
    public Map<String, Object> requestBody(CardSource source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("instructions", INSTRUCTIONS);
        try {
            body.put("input", json.writeValueAsString(source.toPrompt()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning", Map.of("effort", reasoningEffort));
        }
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema", "name", "today_card", "strict", true, "schema", schema())));
        return body;
    }

    static Map<String, Object> schema() {
        Map<String, Object> item = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("time", "word", "pictogram", "mood", "easyText", "guardianText", "homeTip"),
                "properties", Map.of(
                        "time", Map.of("type", "string"),
                        "word", Map.of("type", "string"),
                        "pictogram", Map.of("type", "string", "enum", CardRules.PICTOGRAMS),
                        "mood", Map.of("type", "string", "enum", CardRules.MOODS),
                        "easyText", Map.of("type", "string"),
                        "guardianText", Map.of("type", "string"),
                        "homeTip", Map.of("type", List.of("string", "null"))));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("summary", "items"),
                "properties", Map.of(
                        "summary", Map.of("type", "string"),
                        "items", Map.of("type", "array", "items", item)));
    }

    /** 응답에서 output_text를 꺼내 GeneratedCard로 */
    public GeneratedCard parse(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("AI 응답이 비어 있음");
        }
        String status = response.path("status").asText();
        if (!"completed".equals(status)) {
            throw new IllegalStateException("AI 응답 상태: " + status + " " + response.path("incomplete_details"));
        }
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new IllegalStateException("AI가 거절함");
                }
                if ("output_text".equals(content.path("type").asText())) {
                    try {
                        return json.readValue(content.path("text").asText(), GeneratedCard.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("AI 결과 JSON을 읽지 못함", e);
                    }
                }
            }
        }
        throw new IllegalStateException("AI 응답에 결과 글이 없음");
    }
}
