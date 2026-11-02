package pt.lisboapedala.tariff_pricing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.tariff_pricing.port.PricingRequest;
import pt.lisboapedala.tariff_pricing.port.TariffPricing;

class DefaultTariffPricingTest {

    private final TariffPricing pricing = new DefaultTariffPricing();

    private BigDecimal price(PassType passType, long minutes) {
        return pricing.price(new PricingRequest(passType, minutes));
    }

    @Test
    void assinanteTemOsPrimeiros45MinutosIncluidos() {
        assertThat(price(PassType.ANNUAL, 0)).isEqualByComparingTo("0");
        assertThat(price(PassType.ANNUAL, 45)).isEqualByComparingTo("0");
        assertThat(price(PassType.ANNUAL, 46)).isEqualByComparingTo("0.50");
        assertThat(price(PassType.ANNUAL, 90)).isEqualByComparingTo("1.50");
    }

    @Test
    void ocasionalPagaCadaBlocoDe15Minutos() {
        assertThat(price(PassType.OCCASIONAL, 0)).isEqualByComparingTo("0");
        assertThat(price(PassType.OCCASIONAL, 1)).isEqualByComparingTo("1.00");
        assertThat(price(PassType.OCCASIONAL, 15)).isEqualByComparingTo("1.00");
        assertThat(price(PassType.OCCASIONAL, 52)).isEqualByComparingTo("4.00");
    }

    @Test
    void funcaoPura_mesmoPedidoDaSempreOMesmoValor() {
        PricingRequest request = new PricingRequest(PassType.OCCASIONAL, 52);

        assertThat(pricing.price(request)).isEqualTo(pricing.price(request));
        assertThat(new DefaultTariffPricing().price(request)).isEqualTo(pricing.price(request));
    }

    @Test
    void valorNuncaENegativoECabeEmCentimos() {
        for (PassType passType : PassType.values()) {
            for (long minutes = 0; minutes <= 24 * 60; minutes++) {
                BigDecimal value = price(passType, minutes);
                assertThat(value.signum()).isGreaterThanOrEqualTo(0);
                assertThat(value.stripTrailingZeros().scale()).isLessThanOrEqualTo(2);
            }
        }
    }

    @Test
    void tarifarioDeOutroMunicipioEntraSemAlterarOCodigo() {
        TariffSchedule vizinho = new TariffSchedule("Oeiras", Map.of(
                PassType.ANNUAL, new BlockTariff(30, TariffSchedule.BLOCK_MINUTES, new BigDecimal("0.40")),
                PassType.OCCASIONAL, new BlockTariff(0, TariffSchedule.BLOCK_MINUTES, new BigDecimal("0.80"))));
        TariffPricing outro = new DefaultTariffPricing(vizinho);

        assertThat(outro.price(new PricingRequest(PassType.ANNUAL, 45))).isEqualByComparingTo("0.40");
        assertThat(outro.price(new PricingRequest(PassType.OCCASIONAL, 16))).isEqualByComparingTo("1.60");
        assertThat(price(PassType.OCCASIONAL, 16)).isEqualByComparingTo("2.00");
    }

    @Test
    void tarifarioTemDeCobrirTodosOsTiposDePasse() {
        Map<PassType, TariffRule> incompleto = Map.of(PassType.ANNUAL, minutes -> BigDecimal.ZERO);

        assertThatThrownBy(() -> new TariffSchedule("X", incompleto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OCCASIONAL");
    }

    @Test
    void tarifarioNaoMudaSeOMapaOriginalMudar() {
        Map<PassType, TariffRule> rules = new HashMap<>(TariffSchedule.lisboa().rules());
        TariffSchedule schedule = new TariffSchedule("Lisboa", rules);
        rules.put(PassType.OCCASIONAL, minutes -> BigDecimal.TEN);

        assertThat(schedule.price(PassType.OCCASIONAL, 15)).isEqualByComparingTo("1.00");
        assertThatThrownBy(() -> schedule.rules().put(PassType.ANNUAL, minutes -> BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pedidoNuloERecusado() {
        assertThatThrownBy(() -> pricing.price(null)).isInstanceOf(NullPointerException.class);
    }
}
