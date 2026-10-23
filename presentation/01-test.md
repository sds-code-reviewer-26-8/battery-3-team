# 1. 테스트 세팅

<!-- 세팅한 테스트의 종류별 개수와 테스트 도구를 적으세요. 세팅하지 않은 테스트는 개수에 0을 적거나 행을 지우세요. -->

| 테스트 | 개수 | 테스트 도구 |
|---|:-:|---|
| Unit Test | __개 | ___ |
| Slice Test | 49개 | Spring Boot `@WebMvcTest`(컨트롤러 36개) · `@DataJpaTest`(리포지토리 13개), MockMvc, Mockito(`@MockitoBean`), JUnit 5, AssertJ, H2 |
| 통합 테스트 | __개 | ___ |
| E2E 테스트 | __개 | ___ |
| Robustness 테스트 | __개 | ___ |
| Stress 테스트 | __개 | ___ |
| 성능 테스트 | __개 | ___ |
| Test Coverage | __% | ___ |

<br>

## 테스트 시나리오

<!-- 아래 샘플 이미지를 팀이 제작한 테스트 시나리오 캡처로 교체하세요. 캡처는 presentation/assets/에 올리고 파일명을 맞추세요. -->

아래는 테스트 시나리오 샘플 입니다.

![테스트 시나리오 샘플](assets/TestScenario.png)

---

[← 목차](README.md) · [다음: Loop 설계 →](02-loop.md)
