package io.yak.ops.business.metadata.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 转义函数的注入面是全枚举守护（plan §4.3"单测覆盖"点名列这条）。操作符集合共十个字符。 */
class BooleanModeEscaperTest {

  @Test
  void everyReservedOperatorIsNeutralizedInsideQuotedPhrase() {
    for (Character reserved : BooleanModeEscaper.RESERVED) {
      String term = "a" + reserved + "b";
      // 字面引号在短语内必须成对翻倍——这正是"撑不破短语"的机制本身。
      String literal = reserved == '"' ? "\"\"" : String.valueOf(reserved);
      assertThat(BooleanModeEscaper.quoteTerm(term))
          .as("%s 必须按字面进短语，不得成为查询语法", reserved)
          .isEqualTo("\"a" + literal + "b\"")
          // 除包裹对与内部字面外，不得再多出引号——引号数可数是"短语没被撑破"的最快断言。
          .satisfies(quoted -> {
            long quotes = quoted.chars().filter(c -> c == '"').count();
            assertThat(quotes).isEqualTo(reserved == '"' ? 4 : 2);
          });
    }
  }

  @Test
  void reservedListIsExactlyTheContractSet() {
    assertThat(BooleanModeEscaper.RESERVED)
        .containsExactlyInAnyOrderElementsOf(
            List.of('+', '-', '>', '<', '(', ')', '~', '*', '"', '@'));
  }

  @Test
  void blankAndNullCompileToEmptyBooleanQuery() {
    assertThat(BooleanModeEscaper.toBooleanQuery(null)).isEmpty();
    assertThat(BooleanModeEscaper.toBooleanQuery("   ")).isEmpty();
  }

  @Test
  void whitespaceSplitsTermsQuotedIndependently() {
    assertThat(BooleanModeEscaper.toBooleanQuery(" 订单 > 明细 "))
        .isEqualTo("\"订单\" \">\" \"明细\"");
  }

  @Test
  void embeddedQuoteIsDoubledSoPhraseCannotBeEscapedOutOf() {
    assertThat(BooleanModeEscaper.toBooleanQuery("a\"b"))
        .isEqualTo("\"a\"\"b\"");
  }

  @Test
  void singleCodePointDegradesButTwoCharCjkDoesNot() {
    assertThat(BooleanModeEscaper.shorterThanNgram("表")).isTrue();
    assertThat(BooleanModeEscaper.shorterThanNgram(" 表 ")).isTrue();
    assertThat(BooleanModeEscaper.shorterThanNgram("订单")).isFalse();
    assertThat(BooleanModeEscaper.shorterThanNgram("")).isFalse();
    assertThat(BooleanModeEscaper.shorterThanNgram(null)).isFalse();
  }
}
