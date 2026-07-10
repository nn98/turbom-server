package com.nextstep.infra.geo;

import com.nextstep.domain.site.Coordinate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KoreanTmCoordinateConverterTest {

    @Test
    void epsg5174_중부원점tm을_wgs84로_변환한다() {
        Coordinate coordinate = KoreanTmCoordinateConverter
            .fromEpsg5174(new BigDecimal("212818.475436898"), new BigDecimal("438579.588327304"))
            .orElseThrow();

        assertThat(coordinate.latitude()).isCloseTo(37.449282365, offset(0.000001));
        assertThat(coordinate.longitude()).isCloseTo(127.145653550, offset(0.000001));
    }

    @Test
    void 원본좌표가_없으면_빈값() {
        assertThat(KoreanTmCoordinateConverter.fromEpsg5174(null, BigDecimal.ONE)).isEmpty();
        assertThat(KoreanTmCoordinateConverter.fromEpsg5174(BigDecimal.ONE, null)).isEmpty();
    }
}
