import React, { useState } from 'react';
import useGetSiteSettings from '../../models/useGetSiteSettings';

export default function buildOptions() {

    const {data} = useGetSiteSettings();

    const haploTypeOptions = data?.haplotype.map(item => {
        return {
            value: typeof item === "object" ? item.value : item,
            label: typeof item === "object" ? item.label : item
        }
    }) || [];

    const geneticSexOptions = data?.geneticSex.map(item => {
        return {
            value: typeof item === "object" ? item.value : item,
            label: typeof item === "object" ? item.label : item
        }
    }) || [];

}