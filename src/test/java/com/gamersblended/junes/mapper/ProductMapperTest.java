package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.recommender.ProductRecommendationDTO;
import com.gamersblended.junes.model.Product;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapper() {
        @Override
        public ProductDTO toDTO(Product product) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProductSliderItemDTO toSliderItemDTO(Product product) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProductSliderItemDTO recommendationToSliderItemDTO(ProductRecommendationDTO productRecommendationDTO) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void map_returnsHexString_whenObjectIdIsNotNull() {
        ObjectId objectId = new ObjectId("507f1f77bcf86cd799439011");

        String result = mapper.map(objectId);

        assertThat(result).isEqualTo("507f1f77bcf86cd799439011");
    }

    @Test
    void map_returnsNull_whenObjectIdIsNull() {
        String result = mapper.map(null);

        assertThat(result).isNull();
    }
}
