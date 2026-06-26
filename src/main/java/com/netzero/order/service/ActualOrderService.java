package com.netzero.order.service;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.order.domain.OrderRecommendation;
import com.netzero.order.dto.ActualOrderItem;
import com.netzero.order.dto.ActualOrderLine;
import com.netzero.order.dto.ActualOrderRequest;
import com.netzero.order.dto.ActualOrderResult;
import com.netzero.order.repository.OrderRecommendationRepository;
import com.netzero.store.repository.ItemMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ActualOrderService {

    private final OrderRecommendationRepository orderRecommendationRepository;
    private final ItemMasterRepository itemMasterRepository;

    public ActualOrderService(OrderRecommendationRepository orderRecommendationRepository,
                              ItemMasterRepository itemMasterRepository) {
        this.orderRecommendationRepository = orderRecommendationRepository;
        this.itemMasterRepository = itemMasterRepository;
    }

    @Transactional
    public ActualOrderResult apply(ActualOrderRequest req) {
        int updated = 0;
        List<Long> notFound = new ArrayList<>();
        List<ActualOrderLine> lines = new ArrayList<>();

        for (ActualOrderItem item : req.items()) {
            // Validate: actualQuantity must be >= 0
            if (item.actualQuantity() == null || item.actualQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "actualQuantity must be >= 0 for itemId: " + item.itemId());
            }

            var opt = orderRecommendationRepository
                    .findByStoreIdAndItemIdAndTargetDate(req.storeId(), item.itemId(), req.targetDate());

            if (opt.isEmpty()) {
                notFound.add(item.itemId());
                continue;
            }

            OrderRecommendation rec = opt.get();
            rec.setActualQuantity(item.actualQuantity());
            rec.setActualUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            orderRecommendationRepository.save(rec);
            updated++;

            String itemName = itemMasterRepository.findById(item.itemId())
                    .map(im -> im.getName())
                    .orElse(null);

            lines.add(new ActualOrderLine(
                    rec.getItemId(),
                    itemName,
                    rec.getRecommendedQuantity(),
                    rec.getActualQuantity()));
        }

        return new ActualOrderResult(req.storeId(), req.targetDate(), updated, notFound, lines);
    }
}
