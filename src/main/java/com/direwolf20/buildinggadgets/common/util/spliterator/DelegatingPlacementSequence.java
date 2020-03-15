package com.direwolf20.buildinggadgets.common.util.spliterator;

import com.direwolf20.buildinggadgets.common.building.IPlacementSequence;
import com.direwolf20.buildinggadgets.common.building.Region;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DelegatingPlacementSequence<T, U> implements IPlacementSequence<T> {
    private final IPlacementSequence<U> other;
    private final Function<U, T> mapper;

    public DelegatingPlacementSequence(IPlacementSequence<U> other, Function<U, T> mapper) {
        this.other = other;
        this.mapper = mapper;
    }

    @Nonnull
    @Override
    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Nonnull
    @Override
    public Spliterator<T> spliterator() {
        return new MappingSpliterator<>(other.spliterator(), mapper);
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        return Spliterators.iterator(spliterator());
    }

    @Nonnull
    @Override
    public Region getBoundingBox() {
        return other.getBoundingBox();
    }

    @Override
    public boolean mayContain(int x, int y, int z) {
        return other.mayContain(x, y, z);
    }

    @Nonnull
    @Override
    public IPlacementSequence<T> copy() {
        return new DelegatingPlacementSequence<>(other.copy(), mapper);
    }
}
