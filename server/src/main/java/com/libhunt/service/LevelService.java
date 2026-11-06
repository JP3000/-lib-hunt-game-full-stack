package com.libhunt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.ItemVO;
import com.libhunt.dto.LevelDetailVO;
import com.libhunt.dto.LevelSummaryVO;
import com.libhunt.dto.OptionVO;
import com.libhunt.entity.Item;
import com.libhunt.entity.Level;
import com.libhunt.entity.LevelItem;
import com.libhunt.entity.LevelOption;
import com.libhunt.mapper.ItemMapper;
import com.libhunt.mapper.LevelItemMapper;
import com.libhunt.mapper.LevelMapper;
import com.libhunt.mapper.LevelOptionMapper;
import com.libhunt.util.LocaleSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LevelService {

    private static final int STATUS_ENABLED = 1;

    private final LevelMapper levelMapper;
    private final LevelOptionMapper levelOptionMapper;
    private final LevelItemMapper levelItemMapper;
    private final ItemMapper itemMapper;

    public List<LevelSummaryVO> list(String locale) {
        String loc = LocaleSupport.normalize(locale);
        List<Level> levels = levelMapper.selectList(
                new LambdaQueryWrapper<Level>()
                        .eq(Level::getStatus, STATUS_ENABLED)
                        .orderByAsc(Level::getLevelNo));
        if (levels.isEmpty()) {
            return List.of();
        }

        // 一次性把「关卡-道具」关系与道具文案查出来，避免按关卡逐个查询（N+1）
        List<Long> levelIds = levels.stream().map(Level::getId).toList();
        List<LevelItem> links = levelItemMapper.selectList(
                new LambdaQueryWrapper<LevelItem>()
                        .in(LevelItem::getLevelId, levelIds)
                        .orderByAsc(LevelItem::getSortOrder));

        Map<Long, ItemVO> itemById = links.isEmpty()
                ? Map.of()
                : itemMapper.selectBatchIds(links.stream().map(LevelItem::getItemId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Item::getId,
                        i -> new ItemVO(i.getItemKey(),
                                LocaleSupport.pick(i.getName(), loc),
                                LocaleSupport.pick(i.getDescription(), loc),
                                i.getImageUrl())));

        Map<Long, List<ItemVO>> itemsByLevel = links.stream()
                .filter(link -> itemById.containsKey(link.getItemId()))
                .collect(Collectors.groupingBy(LevelItem::getLevelId,
                        Collectors.mapping(link -> itemById.get(link.getItemId()), Collectors.toList())));

        return levels.stream()
                .map(l -> new LevelSummaryVO(
                        l.getLevelNo(),
                        LocaleSupport.pick(l.getTitle(), loc),
                        itemsByLevel.getOrDefault(l.getId(), List.of())))
                .toList();
    }

    public LevelDetailVO detail(int levelNo, String locale) {
        String loc = LocaleSupport.normalize(locale);

        Level level = levelMapper.selectOne(
                new LambdaQueryWrapper<Level>()
                        .eq(Level::getLevelNo, levelNo)
                        .eq(Level::getStatus, STATUS_ENABLED)
                        .last("LIMIT 1"));
        if (level == null) {
            throw new BusinessException("关卡不存在：" + levelNo);
        }

        List<LevelOption> optionRows = levelOptionMapper.selectList(
                new LambdaQueryWrapper<LevelOption>()
                        .eq(LevelOption::getLevelId, level.getId())
                        .orderByAsc(LevelOption::getSortOrder));

        List<OptionVO> options = optionRows.stream()
                .map(o -> new OptionVO(o.getOptionKey(), LocaleSupport.pick(o.getLabel(), loc)))
                .toList();

        List<LevelItem> links = levelItemMapper.selectList(
                new LambdaQueryWrapper<LevelItem>()
                        .eq(LevelItem::getLevelId, level.getId())
                        .orderByAsc(LevelItem::getSortOrder));

        List<ItemVO> items = loadItems(links, loc);

        return new LevelDetailVO(
                level.getLevelNo(),
                LocaleSupport.pick(level.getTitle(), loc),
                LocaleSupport.pick(level.getStory(), loc),
                LocaleSupport.pick(level.getCompletionStory(), loc),
                LocaleSupport.pick(level.getQuestion(), loc),
                options,
                LocaleSupport.pick(level.getQrLocation(), loc),
                LocaleSupport.pick(level.getQrLocationNote(), loc),
                level.getVideoUrl(),
                level.getStoryImageUrl(),
                LocaleSupport.pick(level.getStoryImageAlt(), loc),
                LocaleSupport.pick(level.getStoryImageCaption(), loc),
                items,
                level.getCorrectOptionKeys() != null && level.getCorrectOptionKeys().size() > 1
        );
    }

    /**
     * 一次批量查回所有道具，避免 N+1，再按关联表顺序重排。
     */
    private List<ItemVO> loadItems(List<LevelItem> links, String loc) {
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = links.stream().map(LevelItem::getItemId).toList();
        Map<Long, Item> itemById = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        return links.stream()
                .map(link -> {
                    Item item = itemById.get(link.getItemId());
                    if (item == null) {
                        return null;
                    }
                    return new ItemVO(
                            item.getItemKey(),
                            LocaleSupport.pick(item.getName(), loc),
                            LocaleSupport.pick(item.getDescription(), loc),
                            item.getImageUrl());
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
