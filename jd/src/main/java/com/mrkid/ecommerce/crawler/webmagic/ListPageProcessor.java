package com.mrkid.ecommerce.crawler.webmagic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrkid.ecommerce.crawler.dto.JDCategoryDTO;
import com.mrkid.ecommerce.crawler.dto.JDSkuDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.handler.SubPageProcessor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * User: xudong
 * Date: 08/11/2016
 * Time: 5:29 PM
 */

@Component
public class ListPageProcessor implements SubPageProcessor {

    private Logger logger = LoggerFactory.getLogger(getClass());


    private static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MatchOther processPage(Page page) {

        try {
            final JsonNode value = objectMapper.readTree(objectMapper.readTree(page.getRawText()).get("value").asText
                    ());

            List<JDSkuDTO> result = new ArrayList<>();
            for (JsonNode node : value.get("wareList")) {
                JDSkuDTO sku = new JDSkuDTO();
                sku.setId(node.get("wareId").asLong());
                sku.setName(node.get("wname").asText());
                sku.setPrice(BigDecimal.valueOf(node.get("jdPrice").asDouble()));
                sku.setCid(node.get("catid").asLong());

                sku.setRawListContent(node.toString());

                if (sku.getId() != 0) {
                    result.add(sku);
                } else {
                    logger.error("extract sku whose id is 0, " + value);
                }
            }

            if (!result.isEmpty()) {
                final Object obj = page.getRequest().getExtra("category");


                JDCategoryDTO category = null;
                if (obj instanceof JDCategoryDTO) {
                    category = (JDCategoryDTO) obj;
                } else {
                    category = objectMapper.readValue(objectMapper.writeValueAsString(obj),
                            JDCategoryDTO.class);
                }
                int p = (Integer) page.getRequest().getExtra("page");
                page.addTargetRequest(RequestHelper.listRequest(category, p + 1));
            }

            page.putField("skus", result);
        } catch (IOException e) {
            logger.error("fail to extract skus", e);
            page.putField("skus", new ArrayList<>());
        }


        return MatchOther.NO;
    }

    @Override
    public boolean match(Request page) {
        return PageType.LIST.equals(page.getExtra(PageType.KEY));
    }
}
